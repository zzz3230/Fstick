package ru.fstick.runtimeservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.runtimeservice.lang.engine.LuaPluginEngine;

@RestController
public class TestController {

    @GetMapping("/init")
    public String test() {
        //LuaPluginEngine engine = new LuaPluginEngine();
        return "";
    }
}
