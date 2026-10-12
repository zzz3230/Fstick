package ru.fstick.registry_service.dto.api.moderation;

import java.util.UUID;

public record PublishRequest(UUID sourceBranchId, String semver, String changelog) {}
