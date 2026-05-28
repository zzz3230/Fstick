package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitPluginRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitScreenshotsRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitVersionRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.*;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;
import ru.fstick.registry_service.dto.service.FileRequest;
import ru.fstick.registry_service.repository.PluginsRepository;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginsServiceTest {

    @Mock private PluginsRepository pluginsRepository;
    @Mock private S3Service s3Service;

    @InjectMocks private PluginsService pluginsService;

    private static final UUID PLUGIN_ID     = UUID.randomUUID();
    private static final UUID VERSION_ID    = UUID.randomUUID();
    private static final UUID AUTHOR_ID     = UUID.randomUUID();
    private static final UUID SCREENSHOT_ID = UUID.randomUUID();

    private PluginData samplePlugin;
    private Version    sampleVersion;
    private Screenshot sampleScreenshot;

    @BeforeEach
    void setUp() {
        samplePlugin = PluginData.builder()
                .id(PLUGIN_ID)
                .authorId(AUTHOR_ID)
                .name("Test Plugin")
                .description("Description")
                .category("Tools")
                .tags(List.of("tag1", "tag2"))
                .status("ACTIVE")
                .iconUrlKey("plugins/" + PLUGIN_ID + "/icon")
                .createdAt("2024-01-01")
                .updatedAt("2024-01-02")
                .build();

        sampleVersion = Version.builder()
                .versionId(VERSION_ID)
                .pluginId(PLUGIN_ID)
                .version("1.0.0")
                .changelog("Initial release")
                .runtime("sv.java@21.0.0")
                .build();

        sampleScreenshot = Screenshot.builder()
                .screenshotId(SCREENSHOT_ID)
                .pluginId(PLUGIN_ID)
                .s3ScreenshotKey("plugins/" + PLUGIN_ID + "/screenshots/shot.png")
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FileRequest fileRequest(String fileName, String type) {
        FileRequest f = new FileRequest();
        f.setFileName(fileName);
        f.setType(type);
        return f;
    }

    private void stubFullPlugin() {
        when(pluginsRepository.getVersionsOfPlugin(PLUGIN_ID)).thenReturn(List.of(sampleVersion));
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of(sampleScreenshot));
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/url");
    }

    // ── getPlugins ────────────────────────────────────────────────────────────

    @Test
    void getPlugins_singlePage_returnsCorrectPagination() {
        when(pluginsRepository.getPlugins(0, 10, "", "", "created_at", "DESC"))
                .thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal("", "")).thenReturn(1);
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/icon");

        PluginsView result = pluginsService.getPlugins(0, 10, "", "", "created_at", "DESC");

        assertEquals(1, result.getItems().size());
        assertEquals(PLUGIN_ID, result.getItems().get(0).getId());
        assertEquals(1, result.getPagination().getTotal());
        assertFalse(result.getPagination().isHasNext());
        assertFalse(result.getPagination().isHasPrev());
    }

    @Test
    void getPlugins_firstOfManyPages_hasNextTrueHasPrevFalse() {
        when(pluginsRepository.getPlugins(0, 5, "", "", "created_at", "DESC"))
                .thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal("", "")).thenReturn(12);
        when(s3Service.generateDownloadUrl(any())).thenReturn("http://cdn/icon");

        PluginsView result = pluginsService.getPlugins(0, 5, "", "", "created_at", "DESC");

        assertTrue(result.getPagination().isHasNext());
        assertFalse(result.getPagination().isHasPrev());
        assertEquals(3, result.getPagination().getTotalPages());
    }

    @Test
    void getPlugins_middlePage_hasBothNextAndPrev() {
        when(pluginsRepository.getPlugins(5, 5, "", "", "created_at", "DESC"))
                .thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal("", "")).thenReturn(12);
        when(s3Service.generateDownloadUrl(any())).thenReturn("http://cdn/icon");

        PluginsView result = pluginsService.getPlugins(1, 5, "", "", "created_at", "DESC");

        assertTrue(result.getPagination().isHasNext());
        assertTrue(result.getPagination().isHasPrev());
    }

    @Test
    void getPlugins_emptyResult_returnsZeroItems() {
        when(pluginsRepository.getPlugins(0, 10, "", "", "created_at", "DESC"))
                .thenReturn(List.of());
        when(pluginsRepository.getPluginsTotal("", "")).thenReturn(0);

        PluginsView result = pluginsService.getPlugins(0, 10, "", "", "created_at", "DESC");

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getPagination().getTotal());
    }

    // ── getPlugin ─────────────────────────────────────────────────────────────

    @Test
    void getPlugin_returnsExtendedViewWithVersionsAndScreenshots() {
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(samplePlugin);
        stubFullPlugin();

        PluginViewExtend result = pluginsService.getPlugin(PLUGIN_ID);

        assertEquals(PLUGIN_ID, result.getId());
        assertEquals(AUTHOR_ID, result.getAuthorId());
        assertEquals(1, result.getVersions().size());
        assertEquals("1.0.0", result.getVersions().get(0).getVersion());
        assertEquals(1, result.getScreenshots().size());
        assertEquals("http://cdn/url", result.getScreenshots().get(0).getScreenshotUrl());
    }

    @Test
    void getPlugin_noVersionsNoScreenshots_returnsEmptyLists() {
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(samplePlugin);
        when(pluginsRepository.getVersionsOfPlugin(PLUGIN_ID)).thenReturn(List.of());
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of());
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/icon");

        PluginViewExtend result = pluginsService.getPlugin(PLUGIN_ID);

        assertTrue(result.getVersions().isEmpty());
        assertTrue(result.getScreenshots().isEmpty());
    }

    // ── updatePlugin ──────────────────────────────────────────────────────────

    @Test
    void updatePlugin_updatesMetadataAndReturnsView() {
        PluginRequest request = new PluginRequest();
        request.setName("Updated");
        request.setDescription("New desc");
        request.setCategory("Tools");
        request.setTags(List.of("a", "b"));

        when(pluginsRepository.updatePlugin(PLUGIN_ID, "Updated", "New desc", "Tools", List.of("a", "b")))
                .thenReturn(samplePlugin);
        when(pluginsRepository.getVersionsOfPlugin(PLUGIN_ID)).thenReturn(List.of());
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of());
        when(s3Service.generateDownloadUrl(any())).thenReturn("http://cdn/icon");

        PluginViewExtend result = pluginsService.updatePlugin(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getId());
        verify(pluginsRepository).updatePlugin(PLUGIN_ID, "Updated", "New desc", "Tools", List.of("a", "b"));
    }

    // ── deletePlugin ──────────────────────────────────────────────────────────

    @Test
    void deletePlugin_returnsOldAndNewStatus() {
        PluginData afterDelete = PluginData.builder()
                .id(PLUGIN_ID).authorId(AUTHOR_ID).name("Test Plugin")
                .description("Description").category("Tools").tags(List.of("tag1", "tag2"))
                .status("DELETED").iconUrlKey("plugins/" + PLUGIN_ID + "/icon")
                .createdAt("2024-01-01").updatedAt("2024-01-02").build();

        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(samplePlugin);
        when(pluginsRepository.deletePlugin(PLUGIN_ID)).thenReturn(afterDelete);

        ChangeStatusResponse result = pluginsService.deletePlugin(PLUGIN_ID);

        assertEquals(PLUGIN_ID, result.getPluginId());
        assertEquals("ACTIVE",  result.getOldStatus());
        assertEquals("DELETED", result.getNewStatus());
    }

    // ── commitPlugin ──────────────────────────────────────────────────────────

    @Test
    void commitPlugin_validVersion_commitsFilesAndReturnsView() {
        CommitPluginRequest request = new CommitPluginRequest();
        request.setVersionId(VERSION_ID);
        request.setKeys(List.of(
                "plugins/" + PLUGIN_ID + "/icon",
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/sv/java/21.0.0/main.jar",
                "plugins/" + PLUGIN_ID + "/screenshots/shot.png"
        ));

        when(pluginsRepository.getVersionPluginId(VERSION_ID)).thenReturn(PLUGIN_ID);
        when(s3Service.getObject(anyString())).thenReturn(new byte[0]);
        when(pluginsRepository.addFiles(eq(PLUGIN_ID), eq(VERSION_ID), any(), any(), any()))
                .thenReturn(samplePlugin);
        stubFullPlugin();

        PluginViewExtend result = pluginsService.commitPlugin(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getId());
        verify(pluginsRepository).addFiles(eq(PLUGIN_ID), eq(VERSION_ID), any(), any(), any());
    }

    @Test
    void commitPlugin_versionBelongsToDifferentPlugin_throwsException() {
        CommitPluginRequest request = new CommitPluginRequest();
        request.setVersionId(VERSION_ID);
        request.setKeys(List.of("plugins/" + PLUGIN_ID + "/icon"));

        when(pluginsRepository.getVersionPluginId(VERSION_ID)).thenReturn(UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> pluginsService.commitPlugin(PLUGIN_ID, request));
    }

    @Test
    void commitPlugin_s3ObjectMissing_throwsException() {
        CommitPluginRequest request = new CommitPluginRequest();
        request.setVersionId(VERSION_ID);
        request.setKeys(List.of(
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/sv/java/21.0.0/missing.jar"
        ));

        when(pluginsRepository.getVersionPluginId(VERSION_ID)).thenReturn(PLUGIN_ID);
        doThrow(new RuntimeException("not found")).when(s3Service).getObject(anyString());

        assertThrows(RuntimeException.class, () -> pluginsService.commitPlugin(PLUGIN_ID, request));
    }

    // ── initPluginUpload ──────────────────────────────────────────────────────

    @Test
    void initPluginUpload_generatesUploadsAndCreatesPlugin() {
        AddPluginRequest request = new AddPluginRequest();
        request.setName("My Plugin");
        request.setDescription("Description");
        request.setCategory("Tools");
        request.setTags(List.of("x"));
        request.setVersion("1.0.0");
        request.setRuntime("sv.java@21.0.0");
        request.setIcon(fileRequest("icon.png", "image/png"));
        request.setFiles(List.of(fileRequest("main.jar", "code/sv.java@21.0.0")));

        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://upload-url");
        when(pluginsRepository.createVersion(any(), eq("1.0.0"), anyString(), eq("sv.java@21.0.0")))
                .thenReturn(VERSION_ID);

        AddPluginResponse result = pluginsService.initPluginUpload(request, AUTHOR_ID);

        assertNotNull(result.getPluginId());
        assertEquals(VERSION_ID, result.getVersionId());
        assertEquals(1, result.getUploads().size());
        assertNotNull(result.getIconUpload());
        assertEquals("http://upload-url", result.getUploads().get(0).getUploadUrl());
        // ключ кода содержит sv/java/21.0.0
        assertTrue(result.getUploads().get(0).getKey().contains("/sv/java/21.0.0/"));
        // ключ иконки
        assertTrue(result.getIconUpload().getKey().contains("/icon"));
        verify(pluginsRepository).addPlugin(any(), eq("My Plugin"), eq("Description"),
                eq("Tools"), eq(List.of("x")), eq(AUTHOR_ID));
    }

    @Test
    void initPluginUpload_clientRuntimeFile_keyContainsClSegment() {
        AddPluginRequest request = new AddPluginRequest();
        request.setName("Plugin"); request.setDescription("Desc");
        request.setVersion("1.0.0"); request.setRuntime("cl.js@18.0.0");
        request.setIcon(fileRequest("icon.png", "image/png"));
        request.setFiles(List.of(fileRequest("app.js", "code/cl.js@18.0.0")));

        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://upload-url");
        when(pluginsRepository.createVersion(any(), any(), any(), any())).thenReturn(VERSION_ID);

        AddPluginResponse result = pluginsService.initPluginUpload(request, AUTHOR_ID);

        assertTrue(result.getUploads().get(0).getKey().contains("/cl/js/18.0.0/"));
    }

    @Test
    void initPluginUpload_invalidFileType_throwsException() {
        AddPluginRequest request = new AddPluginRequest();
        request.setName("P"); request.setDescription("D");
        request.setVersion("1.0.0"); request.setRuntime("sv.java@21.0.0");
        request.setIcon(fileRequest("icon.png", "image/png"));
        request.setFiles(List.of(fileRequest("script.sh", "unknown/something")));

        assertThrows(Exception.class, () -> pluginsService.initPluginUpload(request, AUTHOR_ID));
    }

    @Test
    void initPluginUpload_invalidIconType_throwsException() {
        AddPluginRequest request = new AddPluginRequest();
        request.setName("P"); request.setDescription("D");
        request.setVersion("1.0.0"); request.setRuntime("sv.java@21.0.0");
        request.setIcon(fileRequest("icon.pdf", "code/sv.java@21.0.0")); // не image → key null
        request.setFiles(List.of(fileRequest("main.jar", "code/sv.java@21.0.0")));

        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://upload-url");

        assertThrows(RuntimeException.class, () -> pluginsService.initPluginUpload(request, AUTHOR_ID));
    }

    // ── initVersionUpload ─────────────────────────────────────────────────────

    @Test
    void initVersionUpload_createsVersionAndReturnsUploads() {
        AddPluginVersionRequest request = new AddPluginVersionRequest();
        request.setVersion("2.0.0");
        request.setChangelog("New features");
        request.setRuntime("sv.java@21.0.0");
        request.setFiles(List.of(fileRequest("main.jar", "code/sv.java@21.0.0")));

        when(pluginsRepository.createVersion(PLUGIN_ID, "2.0.0", "New features", "sv.java@21.0.0"))
                .thenReturn(VERSION_ID);
        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://upload-url");

        AddVersionResponse result = pluginsService.initVersionUpload(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getPluginId());
        assertEquals(VERSION_ID, result.getVersionId());
        assertEquals(1, result.getUploads().size());
        assertTrue(result.getUploads().get(0).getKey().contains("/versions/2.0.0/sv/java/21.0.0/"));
    }

    @Test
    void initVersionUpload_imageFileType_throwsException() {
        // initVersionUpload допускает только code/*, не image/*
        AddPluginVersionRequest request = new AddPluginVersionRequest();
        request.setVersion("2.0.0");
        request.setChangelog("changelog");
        request.setRuntime("sv.java@21.0.0");
        request.setFiles(List.of(fileRequest("shot.png", "image/png")));

        when(pluginsRepository.createVersion(any(), any(), any(), any())).thenReturn(VERSION_ID);

        assertThrows(RuntimeException.class, () -> pluginsService.initVersionUpload(PLUGIN_ID, request));
    }

    // ── commitVersion ─────────────────────────────────────────────────────────

    @Test
    void commitVersion_validVersion_addsFilesAndReturnsView() {
        CommitVersionRequest request = new CommitVersionRequest();
        request.setVersionId(VERSION_ID);
        request.setKeys(List.of(
                "plugins/" + PLUGIN_ID + "/versions/2.0.0/sv/java/21.0.0/main.jar"
        ));

        when(pluginsRepository.getVersionPluginId(VERSION_ID)).thenReturn(PLUGIN_ID);
        when(pluginsRepository.addVersion(eq(PLUGIN_ID), eq(VERSION_ID), anyList()))
                .thenReturn(samplePlugin);
        stubFullPlugin();

        PluginViewExtend result = pluginsService.commitVersion(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getId());
        verify(pluginsRepository).addVersion(eq(PLUGIN_ID), eq(VERSION_ID), anyList());
    }

    @Test
    void commitVersion_wrongPlugin_throwsException() {
        CommitVersionRequest request = new CommitVersionRequest();
        request.setVersionId(VERSION_ID);
        request.setKeys(List.of());

        when(pluginsRepository.getVersionPluginId(VERSION_ID)).thenReturn(UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> pluginsService.commitVersion(PLUGIN_ID, request));
    }

    // ── updateScreenshots ─────────────────────────────────────────────────────

    @Test
    void updateScreenshots_imageFiles_generatesUploadUrls() {
        UpdateScreenshotsRequest request = new UpdateScreenshotsRequest();
        request.setFiles(List.of(fileRequest("new-shot.png", "image/png")));

        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://upload-url");

        UpdateScreenshotsResponse result = pluginsService.updateScreenshots(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getPluginId());
        assertEquals(1, result.getUploads().size());
        assertTrue(result.getUploads().get(0).getKey().contains("/screenshots/new-shot.png"));
        assertEquals("http://upload-url", result.getUploads().get(0).getUploadUrl());
    }

    @Test
    void updateScreenshots_nonImageType_throwsException() {
        UpdateScreenshotsRequest request = new UpdateScreenshotsRequest();
        request.setFiles(List.of(fileRequest("code.jar", "code/sv.java@21.0.0")));

        assertThrows(RuntimeException.class, () -> pluginsService.updateScreenshots(PLUGIN_ID, request));
    }

    // ── commitScreenshots ─────────────────────────────────────────────────────

    @Test
    void commitScreenshots_addsScreenshotsAndReturnsView() {
        CommitScreenshotsRequest request = new CommitScreenshotsRequest();
        request.setKeys(List.of(
                "plugins/" + PLUGIN_ID + "/screenshots/new-shot.png"
        ));

        when(pluginsRepository.addScreenshots(eq(PLUGIN_ID), anyList())).thenReturn(samplePlugin);
        when(pluginsRepository.getVersionsOfPlugin(PLUGIN_ID)).thenReturn(List.of());
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of(sampleScreenshot));
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/shot");

        PluginViewExtend result = pluginsService.commitScreenshots(PLUGIN_ID, request);

        assertEquals(PLUGIN_ID, result.getId());
        assertEquals(1, result.getScreenshots().size());
        verify(pluginsRepository).addScreenshots(eq(PLUGIN_ID), argThat(list ->
                list.size() == 1 && list.get(0).contains("/screenshots/")));
    }

    @Test
    void commitScreenshots_keysWithoutScreenshots_addsEmptyList() {
        CommitScreenshotsRequest request = new CommitScreenshotsRequest();
        // ключи только для кода — getAllScreenshots вернёт пустой список
        request.setKeys(List.of(
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/sv/java/21.0.0/main.jar"
        ));

        when(pluginsRepository.addScreenshots(eq(PLUGIN_ID), eq(List.of()))).thenReturn(samplePlugin);
        when(pluginsRepository.getVersionsOfPlugin(PLUGIN_ID)).thenReturn(List.of());
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of());
        when(s3Service.generateDownloadUrl(any())).thenReturn("http://cdn/icon");

        PluginViewExtend result = pluginsService.commitScreenshots(PLUGIN_ID, request);

        assertTrue(result.getScreenshots().isEmpty());
    }

    // ── deleteScreenshot ──────────────────────────────────────────────────────

    @Test
    void deleteScreenshot_deletesFromS3AndRepository() {
        String key = "plugins/" + PLUGIN_ID + "/screenshots/shot.png";
        when(pluginsRepository.getScreenshotKey(PLUGIN_ID, SCREENSHOT_ID)).thenReturn(key);

        pluginsService.deleteScreenshot(PLUGIN_ID, SCREENSHOT_ID);

        verify(s3Service).deleteScreenshot(key);
        verify(pluginsRepository).deleteScreenshot(SCREENSHOT_ID);
    }

    // ── changeStatus ──────────────────────────────────────────────────────────

    @Test
    void changeStatus_toDeleted_returnsCorrectStatuses() {
        PluginData deleted = PluginData.builder()
                .id(PLUGIN_ID).authorId(AUTHOR_ID).name("Test Plugin")
                .description("Description").category("Tools").tags(List.of("tag1", "tag2"))
                .status("DELETED").iconUrlKey("plugins/" + PLUGIN_ID + "/icon")
                .createdAt("2024-01-01").updatedAt("2024-01-02").build();

        when(pluginsRepository.getPlugin(PLUGIN_ID))
                .thenReturn(samplePlugin)
                .thenReturn(deleted);

        ChangeStatusResponse result = pluginsService.changeStatus(PLUGIN_ID, Status.DELETED);

        assertEquals("ACTIVE",  result.getOldStatus());
        assertEquals("DELETED", result.getNewStatus());
    }

    // ── getPluginCodeClient / getPluginCodeServer ─────────────────────────────

    @Test
    void getPluginCodeClient_returnsOnlyClientDownloadLinks() {
        List<String> all = List.of(
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/cl/js/18.0.0/app.js",
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/sv/java/21.0.0/main.jar"
        );
        List<String> clientOnly = List.of(all.get(0));

        when(pluginsRepository.getCode(PLUGIN_ID, "1.0.0", "cl.js@18.0.0")).thenReturn(all);
        when(s3Service.getOnlyClientKeys(all)).thenReturn(clientOnly);
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/client");

        CodeLinksResponse result = pluginsService.getPluginCodeClient(PLUGIN_ID, "1.0.0", "cl.js@18.0.0");

        assertEquals(1, result.getFiles().size());
        verify(s3Service).getOnlyClientKeys(all);
        verify(s3Service, never()).getOnlyServerKeys(any());
    }

    @Test
    void getPluginCodeServer_returnsOnlyServerDownloadLinks() {
        List<String> all = List.of(
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/cl/js/18.0.0/app.js",
                "plugins/" + PLUGIN_ID + "/versions/1.0.0/sv/java/21.0.0/main.jar"
        );
        List<String> serverOnly = List.of(all.get(1));

        when(pluginsRepository.getCode(PLUGIN_ID, "1.0.0", "sv.java@21.0.0")).thenReturn(all);
        when(s3Service.getOnlyServerKeys(all)).thenReturn(serverOnly);
        when(s3Service.generateDownloadUrl(anyString())).thenReturn("http://cdn/server");

        CodeLinksResponse result = pluginsService.getPluginCodeServer(PLUGIN_ID, "1.0.0", "sv.java@21.0.0");

        assertEquals(1, result.getFiles().size());
        verify(s3Service).getOnlyServerKeys(all);
        verify(s3Service, never()).getOnlyClientKeys(any());
    }

    @Test
    void getPluginCodeClient_noCodeFiles_returnsEmptyList() {
        when(pluginsRepository.getCode(PLUGIN_ID, "1.0.0", "cl.js@18.0.0")).thenReturn(List.of());
        when(s3Service.getOnlyClientKeys(List.of())).thenReturn(List.of());

        CodeLinksResponse result = pluginsService.getPluginCodeClient(PLUGIN_ID, "1.0.0", "cl.js@18.0.0");

        assertTrue(result.getFiles().isEmpty());
    }
}