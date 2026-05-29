package ru.fstick.runtimeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.externalservice.IntegrationServiceClient;
import ru.fstick.runtimeservice.externalservice.PluginRegistryService;
import ru.fstick.runtimeservice.lang.PluginRuntimeContext;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;
import ru.fstick.runtimeservice.lang.engine.LuaPluginEngine;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class PluginRuntimeService {

    Map<UUID, LuaPluginEngine> luaPluginEngines =
            Collections.synchronizedMap(new HashMap<>());

    private final PluginRegistryService pluginRegistryService;
    private final StateProviderService stateProviderService;
    private final IntegrationServiceClient integrationServiceClient;

    public CommandExecutionResult executeCommand(String name,
                                                 UUID pluginId,
                                                 String userId,
                                                 String chatId,
                                                 String trackId,
                                                 Map<String, Object> args) {

        if (luaPluginEngines.get(pluginId) == null) {

            log.info(
                    "Initializing Lua plugin engine: pluginId={}",
                    pluginId
            );

            var engine = new LuaPluginEngine();
            engine.initPlugin(pluginRegistryService.getPluginSource(pluginId));

            luaPluginEngines.put(pluginId, engine);
        }

        LuaPluginEngine engine = luaPluginEngines.get(pluginId);

        log.info(
                "Executing plugin command: pluginId={}, command={}, userId={}, chatId={}, trackId={}, args={}",
                pluginId,
                name,
                userId,
                chatId,
                trackId,
                args
        );

        // Единый стейт чата
        PluginRuntimeState state =
                stateProviderService.getState(pluginId, chatId);

        engine.changeContext(
                new PluginRuntimeContext(state, chatId, userId)
        );

        var result = engine.executeCommand(name, args);

        Object stateForResponse;

        state.setValue(engine.getStateValue());

        stateProviderService.commitState(state);

        stateForResponse = state.getValue();

        // Узнаём какие поля user_scoped
        Set<String> userScopedFields =
                engine.extractUserScopedFields();

        log.info(
                "Broadcasting plugin state sync: pluginId={}, chatId={}, userScopedFields={}, state={}",
                pluginId,
                chatId,
                userScopedFields,
                stateForResponse
        );

        integrationServiceClient.broadcastPluginState(
                pluginId,
                chatId,
                stateForResponse,
                userScopedFields
        );

        log.info(
                "Plugin state sync sent successfully: pluginId={}, chatId={}",
                pluginId,
                chatId
        );

        return new CommandExecutionResult(
                result.getStatus(),
                result.getResponse(),
                result.getError(),
                stateForResponse
        );
    }
}