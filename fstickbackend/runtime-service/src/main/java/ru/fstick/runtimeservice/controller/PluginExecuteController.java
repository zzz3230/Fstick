package ru.fstick.runtimeservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.dto.request.CommandRequest;
import ru.fstick.runtimeservice.service.PluginRuntimeService;
import ru.fstick.runtimeservice.service.StateProviderService;
import ru.fstick.runtimeservice.dto.CommandStatus;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class PluginExecuteController {

    private final PluginRuntimeService pluginRuntimeService;
    private final StateProviderService stateProviderService;

    @PostMapping("/command")
    public ResponseEntity<CommandExecutionResult> handleCommand(
            @RequestBody CommandRequest request,
            @RequestHeader("X-User-Id") String userId
    ) {
        var result = pluginRuntimeService.executeCommand(
                request.getName(),
                request.getPluginId(),
                userId,
                request.getChatId(),
                request.getTrackId(),
                request.getArgs()
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/plugins/{pluginId}/state")
    public ResponseEntity<Object> getState(
            @PathVariable UUID pluginId,
            @RequestParam("chat_id") String chatId
    ) {
        var state = stateProviderService.getState(pluginId, chatId);
        return ResponseEntity.ok(state.getValue());
    }
}
