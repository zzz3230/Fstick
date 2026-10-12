package ru.fstick.registry_service.dto.api.editor;

import ru.fstick.registry_service.dto.api.response.CodeResponse;

import java.util.UUID;

public record EditResponse(PluginInfo plugin, BranchInfo branch, Source source, String chatId) {

    public record PluginInfo(UUID id, String name, UUID authorId) {}

    public record BranchInfo(UUID id, String status, Runtime runtime) {}

    public record Runtime(String client, String server) {}

    public record Source(CodeResponse client, CodeResponse server) {}
}
