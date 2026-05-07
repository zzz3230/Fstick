package ru.fstick.installationservice.dto.request;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InstallRequest {
    private UUID pluginId;
    private UUID versionId;
}