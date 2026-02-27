package ru.fstick.runtimeservice.service;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;
import ru.fstick.runtimeservice.controller.PluginExecuteController;
import ru.fstick.runtimeservice.lang.engine.LuaPluginEngine;
import ru.fstick.runtimeservice.utils.FileUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class PluginRuntimeService {

    Map<String, LuaPluginEngine> luaPluginEngines = Collections.synchronizedMap(new HashMap<String, LuaPluginEngine>());


    public String executeCommand(String name,
                               String pluginId,
                               String userId,
                               String chatId,
                               String trackId,
                               Map<String, Object> args) {

        if(luaPluginEngines.get(pluginId) == null){
            var engine = new LuaPluginEngine();
            engine.initPlugin(FileUtils.loadStringFromResource("lua_source/test_plugin.lua"));
            luaPluginEngines.put(pluginId, engine);
        }

        return luaPluginEngines.get(pluginId).executeCommand(
                name,
                args
        );
    }
}
