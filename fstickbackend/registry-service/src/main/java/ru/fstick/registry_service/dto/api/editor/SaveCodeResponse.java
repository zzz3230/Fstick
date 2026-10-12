package ru.fstick.registry_service.dto.api.editor;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record SaveCodeResponse(
        List<Warning> warnings,
        Sha sha,
        boolean reloaded,
        Long retryAfterMs,
        boolean clientChanged
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Warning(String side, String message, Integer line) {}

    public record Sha(String client, String server) {}
}
