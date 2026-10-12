package ru.fstick.registry_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.moderation.GrantModeratorRequest;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.service.ModeratorService;

import java.util.UUID;

@RestController
@RequestMapping("/internal/moderators")
@RequiredArgsConstructor
public class InternalModeratorsController {

    private final ModeratorService moderatorService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void grant(@RequestBody GrantModeratorRequest request) {
        if (request.internalUuid() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed", "internal_uuid is required");
        }
        moderatorService.grant(request.internalUuid(), request.grantedBy());
    }

    @DeleteMapping("/{internalUuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID internalUuid) {
        moderatorService.revoke(internalUuid);
    }
}
