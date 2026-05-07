package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class InstallationShortResponse {
    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String installedBy;
    private OffsetDateTime installedAt;
}