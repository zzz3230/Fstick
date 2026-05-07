package ru.fstick.installationservice.dto.request;

import java.util.UUID;

public class UpdateInstallationRequest {

    private UUID versionId;

    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
}