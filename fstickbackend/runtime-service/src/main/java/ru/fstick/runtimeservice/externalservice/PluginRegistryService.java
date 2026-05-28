package ru.fstick.runtimeservice.externalservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;
import ru.fstick.runtimeservice.dto.PluginDto;
import ru.fstick.runtimeservice.dto.VersionDto;
import ru.fstick.runtimeservice.utils.Downloader;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class PluginRegistryService {

    private final PluginRegistryClient pluginRegistryClient;

    /**
     * Получает исходный код плагина по его идентификатору.
     * Сначала запрашивает метаданные плагина чтобы получить актуальную версию и runtime.
     */
    public String getPluginSource(UUID pluginId) {

        log.info("getPluginSource called, pluginId={}", pluginId);

        PluginDto plugin = pluginRegistryClient.getPlugin(pluginId);
        log.info("Plugin metadata received: id={}, versionsCount={}",
                plugin.getId(),
                plugin.getVersions() == null ? null : plugin.getVersions().size()
        );

        List<VersionDto> versions = plugin.getVersions();
        if (versions == null || versions.isEmpty()) {
            throw new RuntimeException("No versions found for plugin " + pluginId);
        }

        VersionDto version = versions.get(0);
        String versionNumber = version.getVersion();
        String runtime = version.getRuntime();

        log.info("Selected version: versionNumber={}, runtime={}",
                versionNumber, runtime);

        CodeLinksResponse links =
                pluginRegistryClient.getPluginCodeServer(pluginId, versionNumber, runtime);

        log.info("CodeLinksResponse received: {}", links);

        if (links.getFiles() != null && !links.getFiles().isEmpty()) {
            log.info("Files count = {}", links.getFiles().size());

            log.info("First file object = {}", links.getFiles().get(0));

            log.info("First download url = {}",
                    links.getFiles().get(0).getDownload_url());

            log.info("About to download file from S3 url");
        }

        if (links.getFiles() == null || links.getFiles().isEmpty()) {
            log.error("No files returned from registry-service for pluginId={}, version={}, runtime={}",
                    pluginId, versionNumber, runtime);

            throw new RuntimeException("No server code files found for plugin " + pluginId
                    + " version=" + versionNumber + " runtime=" + runtime);
        }

        String url = links.getFiles().get(0).getDownload_url();

        log.info("Downloading final file from url={}", url);

        return new Downloader().download(url);
    }
}