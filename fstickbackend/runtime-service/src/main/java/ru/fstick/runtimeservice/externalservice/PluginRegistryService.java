package ru.fstick.runtimeservice.externalservice;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;
import ru.fstick.runtimeservice.utils.Downloader;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PluginRegistryService {

    private final PluginRegistryClient pluginRegistryClient;

    /**
     * Получает исходный код плагина по его идентификатору
     *
     * @param pluginId уникальный идентификатор плагина
     * @return строка с исходным кодом плагина
    * */
    public String getPluginSource(UUID pluginId) {
        CodeLinksResponse links = pluginRegistryClient.getPluginCodeServer(pluginId, "last", "sv.lua@^0");
        return new Downloader().download(links.getFiles().get(0).getDownloadUrl());
    }
}
