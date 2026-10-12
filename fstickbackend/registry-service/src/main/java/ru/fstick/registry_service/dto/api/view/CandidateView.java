package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CandidateView {
    private UUID branchId;
    private String semver;
    private String status;
}
