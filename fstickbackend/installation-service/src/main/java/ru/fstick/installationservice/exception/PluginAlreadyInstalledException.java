package ru.fstick.installationservice.exception;

public class PluginAlreadyInstalledException extends RuntimeException {
    public PluginAlreadyInstalledException(String pluginId, String chatId) {
        super("Plugin " + pluginId + " is already installed in chat " + chatId);
    }
}