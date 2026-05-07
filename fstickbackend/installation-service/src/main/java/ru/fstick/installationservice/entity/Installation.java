package ru.fstick.installationservice.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Installation {
    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String chatId;
    private String installedBy;
    private OffsetDateTime installedAt;
    private OffsetDateTime updatedAt;
}