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

    /**
     * Получает состояние плагина из хранилища Redis
     * 
     * @param pluginId идентификатор плагина
     * @param chatId идентификатор чата
     * @return объект состояния плагина
     */
    public PluginRuntimeState getState(UUID pluginId, String chatId) {
        Object stateValue = redisTemplate.opsForValue().get(makeKey(pluginId, chatId));
        return new PluginRuntimeState(stateValue, pluginId, chatId);
    }

    /**
     * Сохраняет состояние плагина в хранилище Redis
     * 
     * @param pluginRuntimeState объект состояния плагина для сохранения
     */
    public void commitState(PluginRuntimeState pluginRuntimeState) {
        redisTemplate.opsForValue().set(
                makeKey(
                        pluginRuntimeState.getOwnerPluginId(),
                        pluginRuntimeState.getOwnerChatId()
                ),
                pluginRuntimeState.getValue()
        );
    }

    /**
     * Формирует ключ для хранения состояния в Redis
     * 
     * @param pluginId идентификатор плагина
     * @param chatId идентификатор чата
     * @return сформированный ключ
     */
    private String makeKey(UUID pluginId, String chatId) {
        return "state:" + pluginId + ":" + chatId;
    }
}
