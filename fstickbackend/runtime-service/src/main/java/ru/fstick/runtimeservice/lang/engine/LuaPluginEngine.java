package ru.fstick.runtimeservice.lang.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import ru.fstick.runtimeservice.dto.CommandError;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.dto.CommandStatus;
import ru.fstick.runtimeservice.lang.PluginRuntimeContext;
import ru.fstick.runtimeservice.utils.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LuaPluginEngine {
    private static final Logger log = LogManager.getLogger(LuaPluginEngine.class);

    private final Globals globals;
    private PluginRuntimeContext currentContext;

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

        globals.set("_saveState", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                saveState(luaToJava(arg));
                return LuaValue.NIL;
            }
        });

        globals.set("_loadState", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return javaToLua(globals, loadState());
            }
        });

        globals.get("package")
                .get("preload")
                .set("backend_stdlib", chunk);
    }

    public void changeContext(PluginRuntimeContext newContext) {
        currentContext = newContext;
    }

    public CommandExecutionResult executeCommand(String commandName, Map<String, Object> args) {
        var result = globals.get("ExecuteCommandHandler").call(
                javaToLua(globals, commandName),
                javaToLua(globals, args)
        );

        CommandStatus status = CommandStatus.valueOf(result.get("status").toString());
        Object returnVal = luaToJava(result.get("result"));
        String errorMessage =
                !result.get("error").isnil() && !result.get("error").get("message").isnil() ?
                result.get("error").get("message").toString() : "";

        CommandError error = null;
        if(status != CommandStatus.SUCCESS){
            error = new CommandError(
                    errorMessage,
                    status.toString(),
                    ""
            );
        }

        return new CommandExecutionResult(
                status,
                returnVal,
                error
        );
    }

    Object loadState() {
        return currentContext.getState().getValue();
    }

    void saveState(Object state) {
        currentContext.getState().setValue(state);
    }


    public void initPlugin(String src) {
        try {
            globals.load(src).call();
        } catch (LuaError e) {
            log.error(e.getMessage());
        }
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
                table.set(i + 1, javaToLua(globals, list.get(i))); // Lua массивы с 1
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

    private Object luaToJava(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (value.istable()) {
            LuaTable table = (LuaTable) value;

            // проверяем: это массив (1..n без дырок)?
            int len = table.length();
            boolean isArray = false;

//            for (int i = 1; i <= len; i++) {
//                if (table.get(i).isnil()) {
//                    isArray = false;
//                    break;
//                }
//            }

            if (isArray) {
                List<Object> list = new ArrayList<>();
                for (int i = 1; i <= len; i++) {
                    list.add(luaToJava(table.get(i)));
                }
                return list;
            } else {
                Map<String, Object> map = new HashMap<>();
                LuaValue k = LuaValue.NIL;

                while (true) {
                    Varargs n = table.next(k);
                    k = n.arg1();
                    if (k.isnil()) break;

                    LuaValue v = n.arg(2);
                    map.put(k.tojstring(), luaToJava(v));
                }
                return map;
            }
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isnumber()) {
            return value.todouble(); // можно потом кастить при желании
        }
        if (value.isstring()) {
            return value.tojstring();
        }

        // userdata / function / thread
        return value; // или value.tojstring()
    }
}
