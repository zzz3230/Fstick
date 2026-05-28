package ru.fstick.runtimeservice.externalservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;
import ru.fstick.runtimeservice.dto.FileDownloadData;
import ru.fstick.runtimeservice.utils.Downloader;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginRegistryServiceTest {

    @Mock private PluginRegistryClient pluginRegistryClient;

    @InjectMocks private PluginRegistryService pluginRegistryService;

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final String DOWNLOAD_URL = "http://minio/plugin.lua";
    private static final String LUA_SOURCE   = "function ExecuteCommandHandler() end";

    @Test
    void getPluginSource_downloadsFromFirstFileUrl() {
        CodeLinksResponse response = CodeLinksResponse.builder()
                .files(List.of(FileDownloadData.builder().downloadUrl(DOWNLOAD_URL).build()))
                .build();

        when(pluginRegistryClient.getPluginCodeServer(PLUGIN_ID, "last", "sv.lua@^0"))
                .thenReturn(response);

        // Downloader создаётся через new внутри метода — мокаем конструктор
        try (MockedConstruction<Downloader> mocked = mockConstruction(Downloader.class,
                (mock, ctx) -> when(mock.download(DOWNLOAD_URL)).thenReturn(LUA_SOURCE))) {

            String source = pluginRegistryService.getPluginSource(PLUGIN_ID);

            assertEquals(LUA_SOURCE, source);
            verify(pluginRegistryClient).getPluginCodeServer(PLUGIN_ID, "last", "sv.lua@^0");
        }
    }

    @Test
    void getPluginSource_alwaysUsesLastVersionAndLuaRuntime() {
        CodeLinksResponse response = CodeLinksResponse.builder()
                .files(List.of(FileDownloadData.builder().downloadUrl(DOWNLOAD_URL).build()))
                .build();

        when(pluginRegistryClient.getPluginCodeServer(eq(PLUGIN_ID), eq("last"), eq("sv.lua@^0")))
                .thenReturn(response);

        try (MockedConstruction<Downloader> mocked = mockConstruction(Downloader.class,
                (mock, ctx) -> when(mock.download(any())).thenReturn(""))) {

            pluginRegistryService.getPluginSource(PLUGIN_ID);

            verify(pluginRegistryClient).getPluginCodeServer(PLUGIN_ID, "last", "sv.lua@^0");
        }
    }

    @Test
    void getPluginSource_downloaderThrows_propagatesException() {
        CodeLinksResponse response = CodeLinksResponse.builder()
                .files(List.of(FileDownloadData.builder().downloadUrl(DOWNLOAD_URL).build()))
                .build();

        when(pluginRegistryClient.getPluginCodeServer(any(), any(), any())).thenReturn(response);

        try (MockedConstruction<Downloader> mocked = mockConstruction(Downloader.class,
                (mock, ctx) -> when(mock.download(any())).thenThrow(new RuntimeException("network error")))) {

            assertThrows(RuntimeException.class,
                    () -> pluginRegistryService.getPluginSource(PLUGIN_ID));
        }
    }
}