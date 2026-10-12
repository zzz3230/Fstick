package ru.fstick.registry_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.moderation.ApproveResponse;
import ru.fstick.registry_service.dto.api.moderation.BranchStatusResponse;
import ru.fstick.registry_service.dto.api.moderation.ClaimResponse;
import ru.fstick.registry_service.dto.api.moderation.PublishRequest;
import ru.fstick.registry_service.dto.api.moderation.PublishResponse;
import ru.fstick.registry_service.dto.api.moderation.QueueView;
import ru.fstick.registry_service.dto.api.moderation.RejectRequest;
import ru.fstick.registry_service.service.ModerationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/plugins/{pluginId}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public PublishResponse publish(@RequestHeader("X-User-Id") UUID userId,
                                   @PathVariable UUID pluginId,
                                   @RequestBody PublishRequest request) {
        return moderationService.publish(pluginId, userId, request);
    }

    @GetMapping("/moderation/branches")
    public QueueView queue(@RequestHeader("X-User-Id") UUID userId,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false, defaultValue = "0") int page,
                           @RequestParam(required = false, defaultValue = "20") int limit) {
        return moderationService.queue(userId, status, page, limit);
    }

    @PostMapping("/plugins/{pluginId}/branches/{branchId}/claim")
    public ClaimResponse claim(@RequestHeader("X-User-Id") UUID userId,
                               @PathVariable UUID pluginId,
                               @PathVariable UUID branchId) {
        return moderationService.claim(pluginId, branchId, userId);
    }

    @PostMapping("/plugins/{pluginId}/branches/{branchId}/approve")
    public ApproveResponse approve(@RequestHeader("X-User-Id") UUID userId,
                                   @PathVariable UUID pluginId,
                                   @PathVariable UUID branchId) {
        return moderationService.approve(pluginId, branchId, userId);
    }

    @PostMapping("/plugins/{pluginId}/branches/{branchId}/reject")
    public BranchStatusResponse reject(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID pluginId,
                                       @PathVariable UUID branchId,
                                       @RequestBody(required = false) RejectRequest request) {
        return moderationService.reject(pluginId, branchId, userId, request == null ? null : request.reason());
    }

    @PostMapping("/plugins/{pluginId}/branches/{branchId}/cancel")
    public BranchStatusResponse cancel(@RequestHeader("X-User-Id") UUID userId,
                                       @PathVariable UUID pluginId,
                                       @PathVariable UUID branchId) {
        return moderationService.cancel(pluginId, branchId, userId);
    }
}
