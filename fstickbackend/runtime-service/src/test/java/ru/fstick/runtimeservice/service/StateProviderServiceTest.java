package ru.fstick.runtimeservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateProviderServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks private StateProviderService stateProviderService;

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final String CHAT_ID = "!room:homeserver.org";
    private static final String EXPECTED_KEY = "state:" + PLUGIN_ID + ":" + CHAT_ID;

    @Test
    void getState_existingValue_returnsStateWithValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_KEY)).thenReturn(Map.of("counter", 5));

        PluginRuntimeState state = stateProviderService.getState(PLUGIN_ID, CHAT_ID);

        assertNotNull(state);
        assertEquals(PLUGIN_ID, state.getOwnerPluginId());
        assertEquals(CHAT_ID, state.getOwnerChatId());
        assertEquals(Map.of("counter", 5), state.getValue());
    }

    @Test
    void getState_noValueInRedis_returnsStateWithNullValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_KEY)).thenReturn(null);

        PluginRuntimeState state = stateProviderService.getState(PLUGIN_ID, CHAT_ID);

        assertNull(state.getValue());
        assertEquals(PLUGIN_ID, state.getOwnerPluginId());
        assertEquals(CHAT_ID, state.getOwnerChatId());
    }

    @Test
    void commitState_savesValueWithCorrectKey() {
        PluginRuntimeState state = new PluginRuntimeState("some-state", PLUGIN_ID, CHAT_ID);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        stateProviderService.commitState(state);

        verify(valueOps).set(EXPECTED_KEY, "some-state");
    }

    @Test
    void commitState_nullValue_savesNullToRedis() {
        PluginRuntimeState state = new PluginRuntimeState(null, PLUGIN_ID, CHAT_ID);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        stateProviderService.commitState(state);

        verify(valueOps).set(EXPECTED_KEY, null);
    }

    @Test
    void getState_keyFormat_isCorrect() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(EXPECTED_KEY)).thenReturn(null);

        stateProviderService.getState(PLUGIN_ID, CHAT_ID);

        verify(valueOps).get("state:" + PLUGIN_ID + ":" + CHAT_ID);
    }
}