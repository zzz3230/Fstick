package ru.fstick.registry_service.dto.model;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.BranchStatus;

import java.util.UUID;

@Data
@Builder
public class QueueItem {
    private UUID pluginId;
    private String pluginName;
    private UUID branchId;
    private String semver;
    private String changelog;
    private BranchStatus status;
    private UUID authorId;
    private String submittedAt;
    private UUID claimedBy;
}
