package ru.fstick.registry_service.dto.model;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.BranchStatus;

import java.util.UUID;

@Data
@Builder
public class Branch {
    private UUID id;
    private UUID pluginId;
    private BranchStatus status;
    private String semver;
    private String clientBlobSha;
    private String serverBlobSha;
    private String runtimeClient;
    private String runtimeServer;
    private UUID baseBranchId;
    private String createdAt;
}
