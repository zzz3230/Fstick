package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record PublishResponse(UUID branchId, String semver, String status) {}
