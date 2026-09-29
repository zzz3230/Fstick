package ru.fstick.integrationservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.fstick.integrationservice.exception.ApiException;
import ru.fstick.integrationservice.repository.IdentityRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdentityService {

    private static final Pattern MXID_PATTERN = Pattern.compile("^@[^:]+:.+$");

    private final IdentityRepository repository;

    private final Map<String, UUID> uuidByMxid = new ConcurrentHashMap<>();
    private final Map<UUID, String> mxidByUuid = new ConcurrentHashMap<>();

    public IdentityRepository.Resolved resolve(String mxid) {
        validateMxid(mxid);
        UUID cached = uuidByMxid.get(mxid);
        if (cached != null) {
            return new IdentityRepository.Resolved(cached, false);
        }
        IdentityRepository.Resolved resolved = repository.resolve(mxid);
        remember(mxid, resolved.internalUuid());
        return resolved;
    }

    public Map<String, UUID> resolveBatch(Collection<String> mxids) {
        Map<String, UUID> result = new HashMap<>();
        Set<String> missing = new HashSet<>();
        for (String mxid : mxids) {
            validateMxid(mxid);
            UUID cached = uuidByMxid.get(mxid);
            if (cached != null) {
                result.put(mxid, cached);
            } else {
                missing.add(mxid);
            }
        }
        if (!missing.isEmpty()) {
            Map<String, UUID> resolved = repository.resolveBatch(missing);
            resolved.forEach(this::remember);
            result.putAll(resolved);
        }
        return result;
    }

    public Optional<String> lookup(UUID internalUuid) {
        String cached = mxidByUuid.get(internalUuid);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> mxid = repository.lookup(internalUuid);
        mxid.ifPresent(value -> remember(value, internalUuid));
        return mxid;
    }

    public Map<UUID, String> lookupBatch(Collection<UUID> uuids) {
        Map<UUID, String> result = new HashMap<>();
        Set<UUID> missing = new HashSet<>();
        for (UUID uuid : uuids) {
            String cached = mxidByUuid.get(uuid);
            if (cached != null) {
                result.put(uuid, cached);
            } else {
                missing.add(uuid);
            }
        }
        if (!missing.isEmpty()) {
            Map<UUID, String> found = repository.lookupBatch(missing);
            found.forEach((uuid, mxid) -> remember(mxid, uuid));
            result.putAll(found);
        }
        return result;
    }

    private void remember(String mxid, UUID internalUuid) {
        uuidByMxid.put(mxid, internalUuid);
        mxidByUuid.put(internalUuid, mxid);
    }

    private void validateMxid(String mxid) {
        if (mxid == null || !MXID_PATTERN.matcher(mxid).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_mxid", "mxid must look like @user:domain");
        }
    }
}
