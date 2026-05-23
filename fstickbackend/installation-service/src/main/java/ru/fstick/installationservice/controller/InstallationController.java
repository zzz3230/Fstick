package ru.fstick.installationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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
    @ResponseStatus(HttpStatus.CREATED)
    public InstallationWithWarningsResponse install(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestBody InstallRequest request
    ) {
        return service.install(request, chatId, userId);
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

    @DeleteMapping("/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uninstall(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId
    ) {
        service.uninstall(installationId, userId);
    }
}