package ru.fstick.registry_service.dto.api.moderation;

import java.util.List;
import java.util.UUID;

public record QueueView(List<Item> items, int page, int total) {

    public record Item(UUID pluginId, String pluginName, UUID branchId, String semver, String changelog,
                       String status, Author author, String submittedAt, UUID claimedBy) {}

    public record Author(UUID id) {}
}
