package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record ClaimResponse(UUID branchId, String status, UUID claimedBy) {}
