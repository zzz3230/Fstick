package ru.fstick.installationservice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public class InstallationResponse {

    private UUID installationId;
    private UUID pluginId;
    private UUID versionId;
    private String chatId;
    private String installedBy;
    private OffsetDateTime installedAt;
    private OffsetDateTime updatedAt;

    public InstallationResponse(UUID installationId, UUID pluginId, UUID versionId,
                                String chatId, String installedBy,
                                OffsetDateTime installedAt, OffsetDateTime updatedAt) {
        this.installationId = installationId;
        this.pluginId = pluginId;
        this.versionId = versionId;
        this.chatId = chatId;
        this.installedBy = installedBy;
        this.installedAt = installedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getInstallationId() { return installationId; }
    public UUID getPluginId() { return pluginId; }
    public UUID getVersionId() { return versionId; }
    public String getChatId() { return chatId; }
    public String getInstalledBy() { return installedBy; }
    public OffsetDateTime getInstalledAt() { return installedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}