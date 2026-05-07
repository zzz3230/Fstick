package ru.fstick.installationservice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public class InstallationShortResponse {

    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String installedBy;
    private OffsetDateTime installedAt;

    public InstallationShortResponse(UUID installationId, UUID pluginId, UUID versionId,
                                     String installedBy, OffsetDateTime installedAt) {
        this.installationId = installationId;
        this.pluginId = pluginId;
        this.versionId = versionId;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
    }

    public UUID getInstallationId() { return installationId; }
    public UUID getPluginId() { return pluginId; }
    public UUID getVersionId() { return versionId; }
    public String getInstalledBy() { return installedBy; }
    public OffsetDateTime getInstalledAt() { return installedAt; }
}