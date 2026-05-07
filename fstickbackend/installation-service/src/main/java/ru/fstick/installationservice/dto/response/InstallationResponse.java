package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class InstallationResponse {
    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String chatId;
    private String installedBy;
    private OffsetDateTime installedAt;
    private OffsetDateTime updatedAt;
}