package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PluginViewOwned {
    private UUID id;
    private UUID authorId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String status;
    private String iconUrl;
    private UUID devBranchId;
    private CandidateView candidate;
    private String releasedSemver;
    private LastRejectionView lastRejection;
    private String createdAt;
    private String updatedAt;
}
