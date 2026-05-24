package ru.fstick.installationservice.store;

//import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.fstick.installationservice.dto.request.InstallRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

@Component
public class PendingInstallStore {

    private static final String KEY_PREFIX = "pending_install:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public PendingInstallStore(StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               @Value("${pending.install.ttl-minutes:10}") long ttlMinutes) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String save(InstallRequest request, String chatId, String userId) {
        String token = UUID.randomUUID().toString();
        try {
            PendingInstall pending = new PendingInstall(request, chatId, userId);
            String json = objectMapper.writeValueAsString(pending);
            redis.opsForValue().set(KEY_PREFIX + token, json, ttl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save pending install", e);
        }
        return token;
    }

    public PendingInstall get(String token) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + token);
            if (json == null) return null;
            return objectMapper.readValue(json, PendingInstall.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void remove(String token) {
        redis.delete(KEY_PREFIX + token);
    }

    public record PendingInstall(InstallRequest request, String chatId, String userId) {}
}