package ru.fstick.runtimeservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PluginDto {
    private UUID id;
    private UUID creatorId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String status;
    private String iconUrl;
    private String createdAt;
    private String updatedAt;
}