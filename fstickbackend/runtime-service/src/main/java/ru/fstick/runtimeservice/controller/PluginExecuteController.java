package ru.fstick.runtimeservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.runtimeservice.dto.CommandExecutionResult;
import ru.fstick.runtimeservice.dto.request.CommandRequest;
import ru.fstick.runtimeservice.service.PluginRuntimeService;
import ru.fstick.runtimeservice.dto.CommandStatus;

import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
public class PluginExecuteController {

    private final PluginRuntimeService pluginRuntimeService;


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
}
