package ru.fstick.runtimeservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.service.PluginRuntimeService;
import ru.fstick.runtimeservice.dto.CommandStatus;

import java.util.Map;
import java.util.UUID;

@RestController
public class PluginExecuteController {

    private final PluginRuntimeService pluginRuntimeService;

    public PluginExecuteController(PluginRuntimeService pluginRuntimeService) {
        this.pluginRuntimeService = pluginRuntimeService;
    }


    public record CommandRequest(
            String name,
            UUID pluginId,
            String userId,
            String chatId,
            String trackId,
            Map<String, Object> args
    ) {
    }

    @PostMapping("/command")
    public ResponseEntity<CommandExecutionResult> handleCommand(@RequestBody CommandRequest request) {

        var result = pluginRuntimeService.executeCommand(
                request.name,
                request.pluginId,
                request.userId,
                request.chatId,
                request.trackId,
                request.args
        );

        return ResponseEntity.ok(result);
    }
}
