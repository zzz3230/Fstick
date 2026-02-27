package ru.fstick.runtimeservice.lang.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import ru.fstick.runtimeservice.utils.FileUtils;

import java.util.List;
import java.util.Map;

public class LuaPluginEngine {
    private static final Logger log = LogManager.getLogger(LuaPluginEngine.class);

    private final Globals globals;

    public LuaPluginEngine() {
        globals = JsePlatform.standardGlobals();
        var stdlib = FileUtils.loadStringFromResource("lua_source/backend_stdlib.lua");
        LuaValue chunk = globals.load(stdlib, "backend_stdlib", globals);

        globals.set("print", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                log.info("[LUA] {}", arg.tojstring());
                return LuaValue.NIL;
            }
        });

        globals.get("package")
                .get("preload")
                .set("backend_stdlib", chunk);
    }

    public String executeCommand(String commandName, Map<String, Object> args) {
        var result = globals.get("ExecuteCommandHandler").call(
                javaToLua(globals, commandName),
                javaToLua(globals, args)
        );
        return result.tojstring();
    }

    private void initCommand(LuaValue commandTable) {
        var name = globals.get("PrettyTable").call(commandTable.get("in_schema")).tojstring();
        log.info("Initializing command " + name);

    }

    public String initPlugin(String src){
        try{
            globals.load(src).call();

//            var commandsCount = globals.get("Commands").length();
//            for (var i = 1; i <= commandsCount; i++){
//                var command = globals.get("Commands").get(i);
//                initCommand(command);
//            }
        }
        catch(LuaError e){
            log.error(e.getMessage());
        }
        return "OK";
    }

    private LuaValue javaToLua(Globals globals, Object obj) {
        if (obj == null) {
            return LuaValue.NIL;
        }
        if (obj instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = entry.getKey().toString();
                table.set(key, javaToLua(globals, entry.getValue()));
            }
            return table;
        }
        if (obj instanceof List<?> list) {
            LuaTable table = new LuaTable();
            for (int i = 0; i < list.size(); i++) {
                table.set(i+1, javaToLua(globals, list.get(i))); // Lua массивы с 1
            }
            return table;
        }
        if (obj instanceof Number n) {
            return LuaValue.valueOf(n.doubleValue());
        }
        if (obj instanceof Boolean b) {
            return LuaValue.valueOf(b);
        }
        if (obj instanceof String s) {
            return LuaValue.valueOf(s);
        }

        // fallback
        return LuaValue.valueOf(obj.toString());
    }
}
