package ru.fstick.registry_service.dto.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class Version {
    private UUID versionId;
    private UUID pluginId;
    private String version;
    private String runtime;
    private String changelog;
    private String createdAt;
}
