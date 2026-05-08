package ru.fstick.installationservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.request.UpdateInstallationRequest;
import ru.fstick.installationservice.dto.response.*;
import ru.fstick.installationservice.service.InstallationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/installations")
public class InstallationController {

    private final InstallationService service;

    public InstallationController(InstallationService service) {
        this.service = service;
    }

    // POST /api/v1/installations
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstallationWithWarningsResponse install(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestBody InstallRequest request
    ) {
        InstallationResponse installation = service.install(request, chatId, userId);
        return new InstallationWithWarningsResponse(installation, List.of());
    }

    // GET /api/v1/installations
    @GetMapping
    public PaginatedResponse<InstallationShortResponse> getAll(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.getAllByChatId(chatId, userId, page, limit);
    }

    // GET /api/v1/installations/{installation_id}
    @GetMapping("/{installationId}")
    public InstallationResponse getById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId
    ) {
        return service.getById(installationId, userId);
    }

    // PATCH /api/v1/installations/{installation_id}
    @PatchMapping("/{installationId}")
    public InstallationWithWarningsResponse updateVersion(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId,
            @RequestBody UpdateInstallationRequest request
    ) {
        InstallationResponse installation = service.updateVersion(installationId, request, userId);
        return new InstallationWithWarningsResponse(installation, List.of());
    }

    // DELETE /api/v1/installations/{installation_id}
    @DeleteMapping("/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uninstall(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID installationId
    ) {
        service.uninstall(installationId, userId);
    }
}