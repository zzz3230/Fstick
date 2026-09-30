package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record BranchStatusResponse(UUID branchId, String status) {}
