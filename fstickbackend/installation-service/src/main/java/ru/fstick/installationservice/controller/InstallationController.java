package ru.fstick.installationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.fstick.installationservice.dto.request.ConfirmInstallRequest;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.response.*;
import ru.fstick.installationservice.service.InstallationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/installations")
public class InstallationController {

    private final InstallationService service;

    public InstallationController(InstallationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Object> install(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestBody InstallRequest request
    ) {
        Object result = service.install(request, chatId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public PaginatedResponse<InstallationShortResponse> getAll(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.getAllByChatId(chatId, userId, page, limit);
    }

    @GetMapping("/{installationId}")
    public InstallationResponse getById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId
    ) {
        return service.getById(installationId, userId);
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public InstallationWithWarningsResponse confirmInstall(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody ConfirmInstallRequest request
    ) {
        return service.confirmInstall(request.getConfirmationToken(), userId);
    }

    @DeleteMapping("/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uninstall(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId
    ) {
        service.uninstall(installationId, userId);
    }
}