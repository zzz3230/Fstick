package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;
@Data
@Builder
public class VersionView {
    private UUID id;
    private String version;
    private String changelog;
    private String createdAt;
}
