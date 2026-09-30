package ru.gatewayservice.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityCacheTest {

    @Mock
    private IdentityClient identityClient;

    private IdentityCache cache;

    @BeforeEach
    void setUp() {
        cache = new IdentityCache(identityClient);
    }

    @Test
    void resolve_fillsReverseDirection() {
        UUID uuid = UUID.randomUUID();
        when(identityClient.resolve("@a:hs")).thenReturn(uuid);

        cache.resolve("@a:hs");

        assertEquals(Map.of(uuid, "@a:hs"), cache.lookupBatch(List.of(uuid)));
        verify(identityClient, never()).lookupBatch(any());
    }

    @Test
    void lookupBatch_fetchesOnlyMissing_andCachesResult() {
        UUID known = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        when(identityClient.resolve("@a:hs")).thenReturn(known);
        when(identityClient.lookupBatch(Set.of(other))).thenReturn(Map.of(other, "@b:hs"));
        cache.resolve("@a:hs");

        Map<UUID, String> first = cache.lookupBatch(List.of(known, other));
        cache.lookupBatch(List.of(other));

        assertEquals(Map.of(known, "@a:hs", other, "@b:hs"), first);
        verify(identityClient, times(1)).lookupBatch(any());
        assertEquals(other, cache.resolve("@b:hs"));
        verify(identityClient, times(1)).resolve("@a:hs");
    }
}
