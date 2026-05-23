package ru.fstick.installationservice.exception;

import java.util.UUID;

public class PluginNotFoundException extends RuntimeException {
    public PluginNotFoundException(UUID pluginId) {
        super("Plugin " + pluginId + " not found or not active");
    }
}