package ru.fstick.runtimeservice.externalservice;

import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;
import ru.fstick.runtimeservice.utils.Downloader;

import java.util.UUID;

@Service
public class PluginRegistryService {

    private PluginRegistryClient pluginRegistryClient;

    public PluginRegistryService(PluginRegistryClient pluginRegistryClient) {
        this.pluginRegistryClient = pluginRegistryClient;
    }

    public String getPluginSource(UUID pluginId) {
        CodeLinksResponse links = pluginRegistryClient.getPluginCodeServer(pluginId, "last", "sv.lua@^0");
        return new Downloader().download(links.getFiles().get(0).getDownloadUrl());
    }
}
