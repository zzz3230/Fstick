package ru.fstick.installationservice.exception;

import java.util.UUID;

public class VersionNotFoundException extends RuntimeException {
  public VersionNotFoundException(UUID versionId, UUID pluginId) {
    super("Version " + versionId + " not found for plugin " + pluginId);
  }
}