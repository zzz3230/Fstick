package ru.fstick.runtimeservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.runtimeservice.dto.CommandError;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.dto.CommandStatus;
import ru.fstick.runtimeservice.externalservice.PluginRegistryService;
import ru.fstick.runtimeservice.lang.PluginRuntimeContext;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;
import ru.fstick.runtimeservice.lang.engine.LuaPluginEngine;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginRuntimeServiceTest {

    @Mock private PluginRegistryService pluginRegistryService;
    @Mock private StateProviderService stateProviderService;

    // LuaPluginEngine создаётся внутри сервиса — мокаем через шпионаж на Map
    @InjectMocks private PluginRuntimeService pluginRuntimeService;

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final String USER_ID  = "@user:homeserver.org";
    private static final String CHAT_ID  = "!room:homeserver.org";
    private static final String TRACK_ID = "track-123";

    private PluginRuntimeState sampleState;

    @BeforeEach
    void setUp() {
        sampleState = new PluginRuntimeState(null, PLUGIN_ID, CHAT_ID);
    }

    // ── executeCommand — первый вызов (движок не кэширован) ───────────────────

    @Test
    void executeCommand_firstCall_loadsSourceAndInitsEngine() {
        String luaSource = """
                Commands = {}
                function ExecuteCommandHandler(name, args)
                    return { status = "SUCCESS", result = "ok", error = nil }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        CommandExecutionResult result = pluginRuntimeService.executeCommand(
                "test", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        verify(pluginRegistryService).getPluginSource(PLUGIN_ID);
        assertNotNull(result);
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
    }

    @Test
    void executeCommand_secondCall_doesNotReloadSource() {
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return { status = "SUCCESS", result = nil, error = nil }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        pluginRuntimeService.executeCommand("cmd", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());
        pluginRuntimeService.executeCommand("cmd", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        // Источник должен быть загружен только один раз
        verify(pluginRegistryService, times(1)).getPluginSource(PLUGIN_ID);
    }

    @Test
    void executeCommand_differentPlugins_loadSourceForEach() {
        UUID pluginId2 = UUID.randomUUID();

        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return { status = "SUCCESS", result = nil, error = nil }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(pluginRegistryService.getPluginSource(pluginId2)).thenReturn(luaSource);

        PluginRuntimeState state2 = new PluginRuntimeState(null, pluginId2, CHAT_ID);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);
        when(stateProviderService.getState(pluginId2, CHAT_ID)).thenReturn(state2);

        pluginRuntimeService.executeCommand("cmd", PLUGIN_ID,  USER_ID, CHAT_ID, TRACK_ID, Map.of());
        pluginRuntimeService.executeCommand("cmd", pluginId2, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        verify(pluginRegistryService).getPluginSource(PLUGIN_ID);
        verify(pluginRegistryService).getPluginSource(pluginId2);
    }

    // ── executeCommand — state lifecycle ──────────────────────────────────────

    @Test
    void executeCommand_stateIsLoadedAndCommitted() {
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return { status = "SUCCESS", result = nil, error = nil }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        pluginRuntimeService.executeCommand("cmd", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        verify(stateProviderService).getState(PLUGIN_ID, CHAT_ID);
        verify(stateProviderService).commitState(sampleState);
    }

    // ── executeCommand — результаты Lua ──────────────────────────────────────

    @Test
    void executeCommand_luaReturnsSuccess_resultHasNoError() {
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return { status = "SUCCESS", result = { msg = "hello" }, error = nil }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        CommandExecutionResult result = pluginRuntimeService.executeCommand(
                "greet", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of("name", "World"));

        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertNull(result.getError());
        assertNotNull(result.getResponse());
    }

    @Test
    void executeCommand_luaReturnsRuntimeError_resultHasError() {
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return {
                        status = "RUNTIME_ERROR",
                        result = nil,
                        error = { message = "something broke" }
                    }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        CommandExecutionResult result = pluginRuntimeService.executeCommand(
                "fail", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        assertEquals(CommandStatus.RUNTIME_ERROR, result.getStatus());
        assertNotNull(result.getError());
        assertEquals("something broke", result.getError().getMessage());
    }

    @Test
    void executeCommand_luaReturnsCommandNotFound_resultHasError() {
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return {
                        status = "COMMAND_NOT_FOUND",
                        result = nil,
                        error = { message = "unknown command" }
                    }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        CommandExecutionResult result = pluginRuntimeService.executeCommand(
                "unknown", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of());

        assertEquals(CommandStatus.COMMAND_NOT_FOUND, result.getStatus());
        assertNotNull(result.getError());
    }

    @Test
    void executeCommand_luaPassesArgsCorrectly() {
        // Lua возвращает обратно аргумент из args
        String luaSource = """
                function ExecuteCommandHandler(name, args)
                    return {
                        status = "SUCCESS",
                        result = args.value,
                        error = nil
                    }
                end
                """;

        when(pluginRegistryService.getPluginSource(PLUGIN_ID)).thenReturn(luaSource);
        when(stateProviderService.getState(PLUGIN_ID, CHAT_ID)).thenReturn(sampleState);

        CommandExecutionResult result = pluginRuntimeService.executeCommand(
                "echo", PLUGIN_ID, USER_ID, CHAT_ID, TRACK_ID, Map.of("value", "ping"));

        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertEquals("ping", result.getResponse());
    }
}