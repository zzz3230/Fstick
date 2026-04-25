package ru.fstick.installationservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Installation {

    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String chatId;
    private String installedBy;
    private OffsetDateTime installedAt;
    private OffsetDateTime updatedAt;

    public Installation() {}

    public UUID getInstallationId() { return installationId; }
    public void setInstallationId(UUID installationId) { this.installationId = installationId; }

    public UUID getPluginId() { return pluginId; }
    public void setPluginId(UUID pluginId) { this.pluginId = pluginId; }

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getInstalledBy() { return installedBy; }
    public void setInstalledBy(String installedBy) { this.installedBy = installedBy; }

    public OffsetDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(OffsetDateTime installedAt) { this.installedAt = installedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}