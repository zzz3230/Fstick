package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InternalBranchResponse {
    private UUID branchId;
    private UUID pluginId;
    private String status;
    private String semver;
}
