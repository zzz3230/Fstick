package ru.fstick.registry_service.dto.bd;


import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PluginData {
    private UUID id;
    private UUID creatorId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String currentVersion;
    private String status;
    private String iconUrl;
    private String createdAt;
    private String updatedAt;
}
