package ru.gatewayservice.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class IdentityCache {

    private final IdentityClient identityClient;

    private final Map<String, UUID> uuidByMxid = new ConcurrentHashMap<>();
    private final Map<UUID, String> mxidByUuid = new ConcurrentHashMap<>();

    public UUID resolve(String mxid) {
        UUID cached = uuidByMxid.get(mxid);
        if (cached != null) {
            return cached;
        }
        UUID resolved = identityClient.resolve(mxid);
        remember(mxid, resolved);
        return resolved;
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
            Map<UUID, String> found = identityClient.lookupBatch(missing);
            found.forEach((uuid, mxid) -> remember(mxid, uuid));
            result.putAll(found);
        }
        return result;
    }

    private void remember(String mxid, UUID uuid) {
        uuidByMxid.put(mxid, uuid);
        mxidByUuid.put(uuid, mxid);
    }
}
