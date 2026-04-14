package ru.fstick.registry_service.dto.model;


import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PluginData {
    private UUID id;
    private UUID authorId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String status;
    private String iconUrlKey;
    private String createdAt;
    private String updatedAt;
}
