package ru.fstick.runtimeservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class StateProviderService {

    private final RedisTemplate<String, Object> redisTemplate;

    /** Shared state — один на весь чат (для коллаборативных плагинов) */
    public PluginRuntimeState getState(UUID pluginId, String chatId) {
        Object value = redisTemplate.opsForValue().get(stateKey(pluginId, chatId));
        return new PluginRuntimeState(value, pluginId, chatId);
    }

    public void commitState(PluginRuntimeState state) {
        redisTemplate.opsForValue().set(
                stateKey(state.getOwnerPluginId(), state.getOwnerChatId()),
                state.getValue()
        );
    }

    static String stateKey(UUID pluginId, String chatId) {
        return "state:" + pluginId + ":" + chatId;
    }
}
