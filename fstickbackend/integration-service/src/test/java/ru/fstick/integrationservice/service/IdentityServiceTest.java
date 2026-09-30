package ru.fstick.integrationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.fstick.integrationservice.exception.ApiException;
import ru.fstick.integrationservice.repository.IdentityRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    private static final String MXID = "@ilya:fstick.local";

    @Mock
    private IdentityRepository repository;

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(repository);
    }

    @Test
    void resolve_secondCallServedFromCache() {
        UUID uuid = UUID.randomUUID();
        when(repository.resolve(MXID)).thenReturn(new IdentityRepository.Resolved(uuid, true));

        IdentityRepository.Resolved first = service.resolve(MXID);
        IdentityRepository.Resolved second = service.resolve(MXID);

        assertTrue(first.created());
        assertEquals(uuid, second.internalUuid());
        assertFalse(second.created());
        verify(repository, times(1)).resolve(MXID);
    }

    @Test
    void resolve_populatesReverseCache() {
        UUID uuid = UUID.randomUUID();
        when(repository.resolve(MXID)).thenReturn(new IdentityRepository.Resolved(uuid, true));

        service.resolve(MXID);

        assertEquals(Optional.of(MXID), service.lookup(uuid));
        verify(repository, never()).lookup(any());
    }

    @Test
    void resolve_invalidMxid_throwsBadRequest() {
        for (String invalid : new String[]{null, "", "user:hs", "@user", "@:hs"}) {
            ApiException ex = assertThrows(ApiException.class, () -> service.resolve(invalid));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals("invalid_mxid", ex.getCode());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void resolveBatch_onlyMissingMxidsHitRepository() {
        UUID known = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        when(repository.resolve(MXID)).thenReturn(new IdentityRepository.Resolved(known, false));
        service.resolve(MXID);
        when(repository.resolveBatch(Set.of("@new:hs"))).thenReturn(Map.of("@new:hs", fresh));

        Map<String, UUID> result = service.resolveBatch(List.of(MXID, "@new:hs"));

        assertEquals(Map.of(MXID, known, "@new:hs", fresh), result);
        verify(repository).resolveBatch(Set.of("@new:hs"));
    }

    @Test
    void resolveBatch_invalidMxid_throwsBadRequest() {
        ApiException ex = assertThrows(ApiException.class, () -> service.resolveBatch(List.of(MXID, "bad")));

        assertEquals("invalid_mxid", ex.getCode());
    }

    @Test
    void lookup_unknownUuid_isEmpty() {
        UUID uuid = UUID.randomUUID();
        when(repository.lookup(uuid)).thenReturn(Optional.empty());

        assertTrue(service.lookup(uuid).isEmpty());
    }

    @Test
    void lookupBatch_unknownOmitted_knownCached() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(repository.lookupBatch(anyCollection())).thenReturn(Map.of(known, MXID));

        Map<UUID, String> result = service.lookupBatch(List.of(known, unknown));

        assertEquals(Map.of(known, MXID), result);
        assertEquals(Optional.of(MXID), service.lookup(known));
        verify(repository, never()).lookup(any());
    }
}
