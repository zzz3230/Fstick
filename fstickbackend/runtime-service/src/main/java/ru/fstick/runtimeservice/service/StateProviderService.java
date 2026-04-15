package ru.fstick.runtimeservice.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;

import java.util.UUID;

@Service
public class StateProviderService {
    private RedisTemplate<String, Object> redisTemplate;

    public StateProviderService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public PluginRuntimeState getState(UUID pluginId, String chatId) {
        Object stateValue = redisTemplate.opsForValue().get(makeKey(pluginId, chatId));
        return new PluginRuntimeState(stateValue, pluginId, chatId);
    }

    public void commitState(PluginRuntimeState pluginRuntimeState) {
        redisTemplate.opsForValue().set(
                makeKey(
                        pluginRuntimeState.getOwnerPluginId(),
                        pluginRuntimeState.getOwnerChatId()
                ),
                pluginRuntimeState.getValue()
        );
    }

    private String makeKey(UUID pluginId, String chatId) {
        return "state:" + pluginId + ":" + chatId;
    }
}
