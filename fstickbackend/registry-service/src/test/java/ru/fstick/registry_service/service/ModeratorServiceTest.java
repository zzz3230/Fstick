package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.registry_service.repository.ModeratorRepository;

import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModeratorServiceTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock private ModeratorRepository repository;
    @Mock private Clock clock;

    private ModeratorService service;
    private long now;

    @BeforeEach
    void setUp() {
        now = 1_000_000L;
        lenient().when(clock.millis()).thenAnswer(invocation -> now);
        service = new ModeratorService(repository, clock);
    }

    @Test
    void isModerator_null_isFalseWithoutLookup() {
        assertFalse(service.isModerator(null));
        verifyNoInteractions(repository);
    }

    @Test
    void isModerator_cachedFor60Seconds() {
        when(repository.exists(USER)).thenReturn(true);

        assertTrue(service.isModerator(USER));
        now += ModeratorService.CACHE_TTL_MS - 1;
        assertTrue(service.isModerator(USER));
        verify(repository, times(1)).exists(USER);

        now += 2;
        assertTrue(service.isModerator(USER));
        verify(repository, times(2)).exists(USER);
    }

    @Test
    void grantAndRevoke_evictCache() {
        when(repository.exists(USER)).thenReturn(false, true, false);

        assertFalse(service.isModerator(USER));
        service.grant(USER, null);
        assertTrue(service.isModerator(USER));
        service.revoke(USER);
        assertFalse(service.isModerator(USER));

        verify(repository).grant(USER, null);
        verify(repository).revoke(USER);
    }
}
