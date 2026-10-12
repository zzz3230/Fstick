package ru.fstick.registry_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.repository.ModeratorRepository;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModeratorService {

    static final long CACHE_TTL_MS = 60_000;

    private final ModeratorRepository repository;
    private final Clock clock;
    private final Map<UUID, CachedFlag> cache = new ConcurrentHashMap<>();

    @Autowired
    public ModeratorService(ModeratorRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ModeratorService(ModeratorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean isModerator(UUID internalUuid) {
        if (internalUuid == null) {
            return false;
        }
        long now = clock.millis();
        CachedFlag cached = cache.get(internalUuid);
        if (cached != null && cached.expiresAt() > now) {
            return cached.moderator();
        }
        boolean moderator = repository.exists(internalUuid);
        cache.put(internalUuid, new CachedFlag(moderator, now + CACHE_TTL_MS));
        return moderator;
    }

    public void grant(UUID internalUuid, UUID grantedBy) {
        repository.grant(internalUuid, grantedBy);
        cache.remove(internalUuid);
    }

    public void revoke(UUID internalUuid) {
        repository.revoke(internalUuid);
        cache.remove(internalUuid);
    }

    private record CachedFlag(boolean moderator, long expiresAt) {}
}
