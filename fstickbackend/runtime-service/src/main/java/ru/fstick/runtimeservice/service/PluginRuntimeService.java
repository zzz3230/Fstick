package ru.fstick.runtimeservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.externalservice.PluginRegistryService;
import ru.fstick.runtimeservice.lang.PluginRuntimeContext;
import ru.fstick.runtimeservice.lang.PluginRuntimeState;
import ru.fstick.runtimeservice.lang.engine.LuaPluginEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PluginRuntimeService {

    Map<UUID, LuaPluginEngine> luaPluginEngines = Collections.synchronizedMap(new HashMap<UUID, LuaPluginEngine>());

    private final PluginRegistryService pluginRegistryService;
    private final StateProviderService stateProviderService;

    /**
     * Выполняет команду плагина с заданными параметрами
     * 
     * @param name имя команды для выполнения
     * @param pluginId уникальный идентификатор плагина
     * @param userId идентификатор пользователя, запустившего команду
     * @param chatId идентификатор чата
     * @param trackId идентификатор трека для отслеживания
     * @param args аргументы команды
     * @return результат выполнения команды
     */
    public CommandExecutionResult executeCommand(String name,
                                                 UUID pluginId,
                                                 String userId,
                                                 String chatId,
                                                 String trackId,
                                                 Map<String, Object> args) {

        if(luaPluginEngines.get(pluginId) == null){
            var engine = new LuaPluginEngine();
            String source = pluginRegistryService.getPluginSource(pluginId);

            //engine.initPlugin(FileUtils.loadStringFromResource("lua_source/test_plugin.lua"));
            engine.initPlugin(source);

            luaPluginEngines.put(pluginId, engine);
        }

        LuaPluginEngine engine = luaPluginEngines.get(pluginId);

        PluginRuntimeState state = stateProviderService.getState(pluginId, chatId);
        PluginRuntimeContext context = new PluginRuntimeContext(
                state,
                chatId,
                userId
        );
        engine.changeContext(context);

        var result = engine.executeCommand(name, args);

        stateProviderService.commitState(state);


        return result;
    }
}
