package ru.gatewayservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.gatewayservice.identity.IdentityCache;
import ru.gatewayservice.identity.IdentityUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MxidEnricherTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID GHOST = UUID.randomUUID();

    private IdentityCache cache;
    private MxidEnricher enricher;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        cache = mock(IdentityCache.class);
        enricher = new MxidEnricher(cache);
    }

    private JsonNode run(String json) {
        return mapper.readTree(enricher.enrich(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void enrichesNestedArraysAndObjectsWithOneBatchLookup() {
        when(cache.lookupBatch(anyCollection())).thenReturn(Map.of(ALICE, "@alice:x", BOB, "@bob:x"));

        JsonNode out = run("""
                {"items":[
                  {"id":"p1","author_id":"%s","author":{"id":"%s","name":"Alice"}},
                  {"id":"p2","author_id":"%s","branch":{"claimed_by":"%s"}}
                ],"installed_by":"%s"}""".formatted(ALICE, ALICE, BOB, BOB, ALICE));

        JsonNode first = out.get("items").get(0);
        assertEquals("@alice:x", first.get("author_mxid").asString());
        assertEquals("@alice:x", first.get("author").get("mxid").asString());
        assertEquals("Alice", first.get("author").get("name").asString());
        JsonNode second = out.get("items").get(1);
        assertEquals("@bob:x", second.get("author_mxid").asString());
        assertEquals("@bob:x", second.get("branch").get("claimed_by_mxid").asString());
        assertEquals("@alice:x", out.get("installed_by_mxid").asString());
        verify(cache, times(1)).lookupBatch(anyCollection());
    }

    @Test
    void lookupReceivesDistinctUuids() {
        when(cache.lookupBatch(anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(0);
            assertEquals(Set.of(ALICE, BOB), Set.copyOf(ids));
            assertEquals(2, ids.size());
            return Map.of();
        });

        run("[{\"author_id\":\"%s\"},{\"author_id\":\"%s\"},{\"installed_by\":\"%s\"}]".formatted(ALICE, ALICE, BOB));

        verify(cache, times(1)).lookupBatch(anyCollection());
    }

    @Test
    void unknownOrMissingUuidBecomesNull() {
        when(cache.lookupBatch(anyCollection())).thenReturn(Map.of(ALICE, "@alice:x"));

        JsonNode out = run("{\"a\":{\"author_id\":\"%s\"},\"b\":{\"author_id\":null},\"c\":{\"author_id\":\"junk\"}}"
                .formatted(GHOST));

        assertTrue(out.get("a").get("author_mxid").isNull());
        assertTrue(out.get("b").get("author_mxid").isNull());
        assertTrue(out.get("c").get("author_mxid").isNull());
    }

    @Test
    void integrationDownReturnsOriginalBytes() {
        when(cache.lookupBatch(anyCollection())).thenThrow(new IdentityUnavailableException("down", null));
        byte[] original = ("{\"author_id\":\"" + ALICE + "\"}").getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(original, enricher.enrich(original));
    }

    @Test
    void jsonWithoutUserFieldsIsUntouchedAndSkipsLookup() {
        byte[] original = "{\"id\":\"x\",\"author\":{\"name\":\"n\"}}".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(original, enricher.enrich(original));
        verifyNoInteractions(cache);
    }

    @Test
    void malformedJsonIsReturnedAsIs() {
        byte[] original = "{oops".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(original, enricher.enrich(original));
    }
}
