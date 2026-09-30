package ru.fstick.registry_service;

import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;

import java.util.List;
import java.util.UUID;

public final class TestData {

    public static final String CLIENT_HEX = "c".repeat(64);
    public static final String SERVER_HEX = "5".repeat(64);

    private TestData() {
    }

    public static PluginData plugin(UUID id, UUID authorId) {
        return PluginData.builder()
                .id(id)
                .authorId(authorId)
                .name("Test Plugin")
                .description("Description")
                .category("Tools")
                .tags(List.of("tag1", "tag2"))
                .status("ACTIVE")
                .iconUrlKey("plugins/" + id + "/icon")
                .createdAt("2024-01-01")
                .updatedAt("2024-01-02")
                .build();
    }

    public static Branch branch(UUID pluginId, BranchStatus status) {
        return Branch.builder()
                .id(UUID.randomUUID())
                .pluginId(pluginId)
                .status(status)
                .semver(status == BranchStatus.WORKING ? null : "1.0.0")
                .clientBlobSha(CLIENT_HEX)
                .serverBlobSha(SERVER_HEX)
                .runtimeClient("cl.js@1.0.0")
                .runtimeServer("sv.lua@1.0.0")
                .createdAt("2024-01-01T00:00")
                .build();
    }
}
