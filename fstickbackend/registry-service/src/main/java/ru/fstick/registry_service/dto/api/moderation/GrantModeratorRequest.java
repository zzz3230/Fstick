package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record GrantModeratorRequest(UUID internalUuid, UUID grantedBy) {}
