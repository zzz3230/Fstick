package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class InstallationBranchResponse {
    private UUID installationId;
    private UUID branchId;
    private String branchStatus;
}
