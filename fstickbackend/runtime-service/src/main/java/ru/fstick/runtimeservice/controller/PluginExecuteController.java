package ru.fstick.runtimeservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.runtimeservice.service.PluginRuntimeService;

import java.util.Map;

@RestController
public class PluginExecuteController {

    private final PluginRuntimeService pluginRuntimeService;
    public PluginExecuteController(PluginRuntimeService pluginRuntimeService) {
        this.pluginRuntimeService = pluginRuntimeService;
    }


    public record CommandRequest(
            String name,
            String plugin_id,
            String user_id,
            String chat_id,
            String track_id,
            Map<String, Object> args
    ) {}

    public record CommandResponse(Map<String, Object> response) {}

    @PostMapping("/command")
    public ResponseEntity<CommandResponse> handleCommand(@RequestBody CommandRequest request) {

        var result = pluginRuntimeService.executeCommand(
                request.name,
                request.plugin_id,
                request.user_id,
                request.chat_id,
                request.track_id,
                request.args
        );

        return ResponseEntity.ok(new CommandResponse(Map.of("result", result)));
    }
}
