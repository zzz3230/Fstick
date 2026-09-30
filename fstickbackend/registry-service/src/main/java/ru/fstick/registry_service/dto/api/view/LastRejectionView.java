package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LastRejectionView {
    private String reason;
    private String at;
    private String semver;
    private UUID branchId;
}
