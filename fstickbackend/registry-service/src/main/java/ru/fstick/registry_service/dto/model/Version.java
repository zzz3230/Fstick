package ru.fstick.registry_service.dto.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class Version {
    private UUID id;
    private String version;
    private String changelog;
    private String createdAt;
}
