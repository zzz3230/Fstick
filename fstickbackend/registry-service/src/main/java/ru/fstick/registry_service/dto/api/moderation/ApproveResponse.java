package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record ApproveResponse(UUID branchId, String status, String semver) {}
