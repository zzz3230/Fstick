package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class BranchView {
    private UUID id;
    private String status;
    private String semver;
    private RuntimeView runtime;
    private String createdAt;

    @Data
    @Builder
    public static class RuntimeView {
        private String client;
        private String server;
    }
}
