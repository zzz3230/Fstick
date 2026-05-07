package ru.fstick.installationservice.dto.request;

import java.util.UUID;

public class InstallRequest {

    private UUID pluginId;
    private UUID versionId;

    public UUID getPluginId() { return pluginId; }
    public void setPluginId(UUID pluginId) { this.pluginId = pluginId; }

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
}