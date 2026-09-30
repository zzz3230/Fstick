package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.AddPluginRequest;
import ru.fstick.registry_service.dto.api.request.PluginRequest;
import ru.fstick.registry_service.dto.api.request.UpdateScreenshotsRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitAssetsRequest;
import ru.fstick.registry_service.dto.api.response.AddPluginResponse;
import ru.fstick.registry_service.dto.api.response.ChangeStatusResponse;
import ru.fstick.registry_service.dto.api.response.UpdateScreenshotsResponse;
import ru.fstick.registry_service.dto.api.view.CandidateView;
import ru.fstick.registry_service.dto.api.view.LastRejectionView;
import ru.fstick.registry_service.dto.api.view.PluginViewExtend;
import ru.fstick.registry_service.dto.api.view.PluginViewOwned;
import ru.fstick.registry_service.dto.api.view.PluginViewShrink;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.service.FileRequest;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static ru.fstick.registry_service.TestData.branch;
import static ru.fstick.registry_service.TestData.plugin;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginsServiceTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID STRANGER_ID = UUID.randomUUID();
    private static final UUID SCREENSHOT_ID = UUID.randomUUID();
    private static final String CHAT = "!room:example.org";

    @Mock private PluginsRepository pluginsRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private S3Service s3Service;
    @Mock private BlobStore blobStore;
    @Mock private TemplateProvider templateProvider;
    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;
    @Mock private ModeratorService moderatorService;

    private PluginsService pluginsService;
    private PluginData samplePlugin;

    @BeforeEach
    void setUp() {
        AccessGuard accessGuard = new AccessGuard(pluginsRepository, installationClient, integrationClient, moderatorService);
        pluginsService = new PluginsService(pluginsRepository, branchRepository, s3Service, blobStore, templateProvider, accessGuard);

        samplePlugin = plugin(PLUGIN_ID, AUTHOR_ID);
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(samplePlugin));
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(samplePlugin);
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of());
        when(s3Service.generateDownloadUrl(anyString(), eq(true))).thenAnswer(inv -> "http://minio/" + inv.getArgument(0));
    }

    private FileRequest fileRequest(String fileName, String type) {
        FileRequest file = new FileRequest();
        file.setFileName(fileName);
        file.setType(type);
        return file;
    }

    private AddPluginRequest addRequest(FileRequest icon) {
        AddPluginRequest request = new AddPluginRequest();
        request.setName("P");
        request.setDescription("D");
        request.setCategory("Tools");
        request.setTags(List.of("t"));
        request.setIcon(icon);
        return request;
    }

    private static ApiException thrown(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(ApiException.class, call);
    }

    // ── getPlugins ────────────────────────────────────────────────────────────

    @Test
    void getPlugins_publicList_usesNullOwnerAndReturnsReleasedSemver() {
        PluginData released = plugin(PLUGIN_ID, AUTHOR_ID);
        released.setReleasedSemver("1.2.0");
        when(pluginsRepository.getPlugins(0, 20, "", "", "plugin_name", "asc", null)).thenReturn(List.of(released));
        when(pluginsRepository.getPluginsTotal("", "", null)).thenReturn(1);

        PluginsView<PluginViewShrink> view = pluginsService.getPlugins(0, 20, "", "", "plugin_name", "asc");

        assertEquals(1, view.getItems().size());
        assertEquals("1.2.0", view.getItems().get(0).getReleasedSemver());
        assertEquals("http://minio/plugins/" + PLUGIN_ID + "/icon", view.getItems().get(0).getIconUrl());
        assertEquals(1, view.getPagination().getTotal());
        assertFalse(view.getPagination().isHasNext());
        assertFalse(view.getPagination().isHasPrev());
    }

    @Test
    void getPlugins_firstOfManyPages_hasNextTrueHasPrevFalse() {
        when(pluginsRepository.getPlugins(anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal(any(), any(), any())).thenReturn(50);

        PluginsView<PluginViewShrink> view = pluginsService.getPlugins(0, 10, "", "", "plugin_name", "asc");

        assertEquals(5, view.getPagination().getTotalPages());
        assertTrue(view.getPagination().isHasNext());
        assertFalse(view.getPagination().isHasPrev());
    }

    @Test
    void getPlugins_middlePage_hasBothNextAndPrev() {
        when(pluginsRepository.getPlugins(anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal(any(), any(), any())).thenReturn(50);

        PluginsView<PluginViewShrink> view = pluginsService.getPlugins(2, 10, "", "", "plugin_name", "asc");

        assertTrue(view.getPagination().isHasNext());
        assertTrue(view.getPagination().isHasPrev());
        verify(pluginsRepository).getPlugins(20, 10, "", "", "plugin_name", "asc", null);
    }

    @Test
    void getPlugins_emptyResult_returnsZeroItems() {
        when(pluginsRepository.getPlugins(anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(pluginsRepository.getPluginsTotal(any(), any(), any())).thenReturn(0);

        PluginsView<PluginViewShrink> view = pluginsService.getPlugins(0, 20, "", "", "plugin_name", "asc");

        assertTrue(view.getItems().isEmpty());
        assertEquals(0, view.getPagination().getTotalPages());
    }

    @Test
    void getPlugins_pluginWithoutIcon_usesTemplateIcon() {
        samplePlugin.setIconUrlKey(null);
        when(pluginsRepository.getPlugins(anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal(any(), any(), any())).thenReturn(1);

        PluginsView<PluginViewShrink> view = pluginsService.getPlugins(0, 20, "", "", "plugin_name", "asc");

        assertEquals("http://minio/" + TemplateProvider.ICON_KEY, view.getItems().get(0).getIconUrl());
    }

    // ── getOwnedPlugins ───────────────────────────────────────────────────────

    @Test
    void getOwnedPlugins_filtersByCallerAndExposesDevBranch() {
        UUID devBranch = UUID.randomUUID();
        samplePlugin.setDevBranchId(devBranch);
        when(pluginsRepository.getPlugins(0, 20, "", "", "plugin_name", "asc", AUTHOR_ID)).thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal("", "", AUTHOR_ID)).thenReturn(1);

        PluginsView<PluginViewOwned> view = pluginsService.getOwnedPlugins(AUTHOR_ID, 0, 20, "", "", "plugin_name", "asc");

        PluginViewOwned item = view.getItems().get(0);
        assertEquals(devBranch, item.getDevBranchId());
        assertNull(item.getReleasedSemver());
        assertNull(item.getCandidate());
        assertNull(item.getLastRejection());
        assertEquals(1, view.getPagination().getTotal());
    }

    @Test
    void getOwnedPlugins_exposesCandidateAndLastRejection() {
        UUID candidateBranch = UUID.randomUUID();
        samplePlugin.setCandidate(CandidateView.builder().branchId(candidateBranch).semver("1.2.0").status("WAITING_APPROVE").build());
        samplePlugin.setLastRejection(LastRejectionView.builder().reason("no").semver("1.1.0").build());
        when(pluginsRepository.getPlugins(0, 20, "", "", "plugin_name", "asc", AUTHOR_ID)).thenReturn(List.of(samplePlugin));
        when(pluginsRepository.getPluginsTotal("", "", AUTHOR_ID)).thenReturn(1);

        PluginViewOwned item = pluginsService.getOwnedPlugins(AUTHOR_ID, 0, 20, "", "", "plugin_name", "asc").getItems().get(0);

        assertEquals(candidateBranch, item.getCandidate().getBranchId());
        assertEquals("no", item.getLastRejection().getReason());
    }

    // ── getPlugin visibility ──────────────────────────────────────────────────

    private void stubBranches(Branch... branches) {
        when(branchRepository.findByPlugin(PLUGIN_ID)).thenReturn(List.of(branches));
    }

    @Test
    void getPlugin_author_seesAllBranches() {
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING),
                branch(PLUGIN_ID, BranchStatus.RELEASED),
                branch(PLUGIN_ID, BranchStatus.WAITING_APPROVE),
                branch(PLUGIN_ID, BranchStatus.REJECTED),
                branch(PLUGIN_ID, BranchStatus.CANCELLED));

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, AUTHOR_ID, null);

        assertEquals(5, view.getBranches().size());
        assertEquals("cl.js@1.0.0", view.getBranches().get(0).getRuntime().getClient());
        assertEquals("sv.lua@1.0.0", view.getBranches().get(0).getRuntime().getServer());
    }

    @Test
    void getPlugin_lastRejection_visibleToAuthorOnly() {
        samplePlugin.setLastRejection(LastRejectionView.builder().reason("no").semver("1.1.0").build());
        stubBranches(branch(PLUGIN_ID, BranchStatus.RELEASED));

        assertEquals("no", pluginsService.getPlugin(PLUGIN_ID, AUTHOR_ID, null).getLastRejection().getReason());
        assertNull(pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, null).getLastRejection());
    }

    @Test
    void getPlugin_stranger_seesReleasedOnly() {
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING),
                branch(PLUGIN_ID, BranchStatus.RELEASED),
                branch(PLUGIN_ID, BranchStatus.WAITING_APPROVE));

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, null);

        assertEquals(1, view.getBranches().size());
        assertEquals("RELEASED", view.getBranches().get(0).getStatus());
    }

    @Test
    void getPlugin_anonymous_seesReleasedOnly() {
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING), branch(PLUGIN_ID, BranchStatus.RELEASED));

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, null, null);

        assertEquals(1, view.getBranches().size());
    }

    @Test
    void getPlugin_strangerAndNoReleasedBranch_404() {
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING), branch(PLUGIN_ID, BranchStatus.WAITING_APPROVE));

        ApiException ex = thrown(() -> pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, null));

        assertEquals(404, ex.getStatus().value());
        assertEquals("plugin_not_found", ex.getCode());
    }

    @Test
    void getPlugin_chatMemberWithDevInstall_seesDevBranchToo() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        stubBranches(dev, branch(PLUGIN_ID, BranchStatus.RELEASED));
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER_ID)).thenReturn(true);

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, CHAT);

        assertEquals(2, view.getBranches().size());
    }

    @Test
    void getPlugin_chatMemberWithDevInstallAndNoRelease_seesDevBranchInsteadOf404() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        stubBranches(dev);
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER_ID)).thenReturn(true);

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, CHAT);

        assertEquals(1, view.getBranches().size());
        assertEquals("WORKING", view.getBranches().get(0).getStatus());
    }

    @Test
    void getPlugin_chatCheckFails_404() {
        Branch dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        stubBranches(dev);
        when(installationClient.resolve(PLUGIN_ID, CHAT)).thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER_ID)).thenReturn(false);

        assertEquals(404, thrown(() -> pluginsService.getPlugin(PLUGIN_ID, STRANGER_ID, CHAT)).getStatus().value());
    }

    @Test
    void getPlugin_deletedPlugin_404() {
        samplePlugin.setStatus("DELETED");
        stubBranches(branch(PLUGIN_ID, BranchStatus.RELEASED));

        assertEquals(404, thrown(() -> pluginsService.getPlugin(PLUGIN_ID, AUTHOR_ID, null)).getStatus().value());
    }

    @Test
    void getPlugin_unknownPlugin_404() {
        UUID unknown = UUID.randomUUID();
        when(pluginsRepository.findPlugin(unknown)).thenReturn(Optional.empty());

        assertEquals("plugin_not_found", thrown(() -> pluginsService.getPlugin(unknown, AUTHOR_ID, null)).getCode());
    }

    @Test
    void getPlugin_includesScreenshotsAndIcon() {
        stubBranches(branch(PLUGIN_ID, BranchStatus.RELEASED));
        when(pluginsRepository.getScreenshots(PLUGIN_ID)).thenReturn(List.of(Screenshot.builder()
                .screenshotId(SCREENSHOT_ID).pluginId(PLUGIN_ID).s3ScreenshotKey("plugins/x/screenshots/a.png").build()));

        PluginViewExtend view = pluginsService.getPlugin(PLUGIN_ID, null, null);

        assertEquals(1, view.getScreenshots().size());
        assertEquals("http://minio/plugins/x/screenshots/a.png", view.getScreenshots().get(0).getScreenshotUrl());
        assertEquals("http://minio/plugins/" + PLUGIN_ID + "/icon", view.getIconUrl());
    }

    // ── ownership ─────────────────────────────────────────────────────────────

    @Test
    void updatePlugin_author_updatesAndReturnsView() {
        PluginRequest request = new PluginRequest();
        request.setName("New");
        request.setDescription("Desc");
        request.setCategory("Tools");
        request.setTags(List.of("a"));
        when(pluginsRepository.updatePlugin(PLUGIN_ID, "New", "Desc", "Tools", List.of("a"))).thenReturn(samplePlugin);
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING));

        PluginViewExtend view = pluginsService.updatePlugin(PLUGIN_ID, AUTHOR_ID, request);

        assertEquals(PLUGIN_ID, view.getId());
        assertEquals(1, view.getBranches().size());
    }

    @Test
    void updatePlugin_nonAuthor_403AndNothingWritten() {
        ApiException ex = thrown(() -> pluginsService.updatePlugin(PLUGIN_ID, STRANGER_ID, new PluginRequest()));

        assertEquals(403, ex.getStatus().value());
        verify(pluginsRepository, never()).updatePlugin(any(), any(), any(), any(), any());
    }

    @Test
    void updatePlugin_unknownPlugin_404() {
        UUID unknown = UUID.randomUUID();
        when(pluginsRepository.findPlugin(unknown)).thenReturn(Optional.empty());

        assertEquals(404, thrown(() -> pluginsService.updatePlugin(unknown, AUTHOR_ID, new PluginRequest())).getStatus().value());
    }

    @Test
    void deletePlugin_author_returnsOldAndNewStatus() {
        PluginData deleted = plugin(PLUGIN_ID, AUTHOR_ID);
        deleted.setStatus("DELETED");
        when(pluginsRepository.deletePlugin(PLUGIN_ID)).thenReturn(deleted);

        ChangeStatusResponse response = pluginsService.deletePlugin(PLUGIN_ID, AUTHOR_ID);

        assertEquals("ACTIVE", response.getOldStatus());
        assertEquals("DELETED", response.getNewStatus());
    }

    @Test
    void deletePlugin_nonAuthor_403() {
        assertEquals(403, thrown(() -> pluginsService.deletePlugin(PLUGIN_ID, STRANGER_ID)).getStatus().value());
        verify(pluginsRepository, never()).deletePlugin(any());
    }

    @Test
    void changeStatus_author_toHidden() {
        PluginData hidden = plugin(PLUGIN_ID, AUTHOR_ID);
        hidden.setStatus("HIDDEN");
        when(pluginsRepository.getPlugin(PLUGIN_ID)).thenReturn(hidden);

        ChangeStatusResponse response = pluginsService.changeStatus(PLUGIN_ID, AUTHOR_ID, Status.HIDDEN);

        assertEquals("HIDDEN", response.getNewStatus());
        verify(pluginsRepository).changeStatus(PLUGIN_ID, Status.HIDDEN);
    }

    @Test
    void changeStatus_archived_422() {
        ApiException ex = thrown(() -> pluginsService.changeStatus(PLUGIN_ID, AUTHOR_ID, Status.ARCHIVED));

        assertEquals(422, ex.getStatus().value());
        verify(pluginsRepository, never()).changeStatus(any(), any());
    }

    @Test
    void changeStatus_nonAuthor_403() {
        assertEquals(403, thrown(() -> pluginsService.changeStatus(PLUGIN_ID, STRANGER_ID, Status.ACTIVE)).getStatus().value());
        verify(pluginsRepository, never()).changeStatus(any(), any());
    }

    // ── createPlugin ──────────────────────────────────────────────────────────

    private void stubCreate(UUID devBranchId) {
        when(templateProvider.getClientCode()).thenReturn("client-template");
        when(templateProvider.getServerCode()).thenReturn("server-template");
        when(blobStore.put(anyString(), eq("client-template"))).thenReturn("c".repeat(64));
        when(blobStore.put(anyString(), eq("server-template"))).thenReturn("5".repeat(64));
        when(branchRepository.createDevBranch(any(), anyString(), anyString())).thenReturn(devBranchId);
    }

    @Test
    void createPlugin_withoutIcon_createsDevBranchFromTemplates() {
        UUID devBranchId = UUID.randomUUID();
        stubCreate(devBranchId);

        AddPluginResponse response = pluginsService.createPlugin(addRequest(null), AUTHOR_ID);

        assertEquals(devBranchId, response.getDevBranchId());
        assertNull(response.getIconUpload());
        verify(s3Service, never()).generateUploadUrl(anyString());

        ArgumentCaptor<UUID> pluginId = ArgumentCaptor.forClass(UUID.class);
        verify(pluginsRepository).addPlugin(pluginId.capture(), eq("P"), eq("D"), eq("Tools"), eq(List.of("t")), eq(AUTHOR_ID));
        assertEquals(pluginId.getValue(), response.getPluginId());
        verify(blobStore).put(pluginId.getValue().toString(), "client-template");
        verify(blobStore).put(pluginId.getValue().toString(), "server-template");
        verify(branchRepository).createDevBranch(pluginId.getValue(), "c".repeat(64), "5".repeat(64));
    }

    @Test
    void createPlugin_withIcon_returnsPresignedUpload() {
        stubCreate(UUID.randomUUID());
        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://minio/upload");

        AddPluginResponse response = pluginsService.createPlugin(addRequest(fileRequest("icon.png", "image/png")), AUTHOR_ID);

        assertEquals("plugins/" + response.getPluginId() + "/icon", response.getIconUpload().getKey());
        assertEquals("http://minio/upload", response.getIconUpload().getUploadUrl());
    }

    @Test
    void createPlugin_codeTypeAsIcon_422AndNothingCreated() {
        ApiException ex = thrown(() -> pluginsService.createPlugin(addRequest(fileRequest("icon", "code/sv.lua@1.0.0")), AUTHOR_ID));

        assertEquals(422, ex.getStatus().value());
        verifyNoInteractions(blobStore, branchRepository);
        verify(pluginsRepository, never()).addPlugin(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createPlugin_malformedIconType_422() {
        ApiException ex = thrown(() -> pluginsService.createPlugin(addRequest(fileRequest("icon", "png")), AUTHOR_ID));

        assertEquals("invalid_type", ex.getCode());
    }

    // ── screenshots and assets ────────────────────────────────────────────────

    @Test
    void updateScreenshots_imageFiles_generatesUploadUrls() {
        UpdateScreenshotsRequest request = new UpdateScreenshotsRequest();
        request.setFiles(List.of(fileRequest("shot.png", "image/png")));
        when(s3Service.generateUploadUrl(anyString())).thenReturn("http://minio/upload");

        UpdateScreenshotsResponse response = pluginsService.updateScreenshots(PLUGIN_ID, AUTHOR_ID, request);

        assertEquals(1, response.getUploads().size());
        assertEquals("plugins/" + PLUGIN_ID + "/screenshots/shot.png", response.getUploads().get(0).getKey());
    }

    @Test
    void updateScreenshots_nonImageType_422() {
        UpdateScreenshotsRequest request = new UpdateScreenshotsRequest();
        request.setFiles(List.of(fileRequest("main.lua", "code/sv.lua@1.0.0")));

        assertEquals(422, thrown(() -> pluginsService.updateScreenshots(PLUGIN_ID, AUTHOR_ID, request)).getStatus().value());
    }

    @Test
    void updateScreenshots_nonAuthor_403() {
        UpdateScreenshotsRequest request = new UpdateScreenshotsRequest();
        request.setFiles(List.of(fileRequest("shot.png", "image/png")));

        assertEquals(403, thrown(() -> pluginsService.updateScreenshots(PLUGIN_ID, STRANGER_ID, request)).getStatus().value());
        verify(s3Service, never()).generateUploadUrl(anyString());
    }

    private CommitAssetsRequest commit(String... keys) {
        CommitAssetsRequest request = new CommitAssetsRequest();
        request.setKeys(List.of(keys));
        return request;
    }

    @Test
    void commitAssets_iconAndScreenshot_setsIconAndAddsScreenshots() {
        String icon = "plugins/" + PLUGIN_ID + "/icon";
        String shot = "plugins/" + PLUGIN_ID + "/screenshots/a.png";
        when(s3Service.exists(anyString())).thenReturn(true);
        when(pluginsRepository.addScreenshots(PLUGIN_ID, List.of(shot))).thenReturn(samplePlugin);
        stubBranches(branch(PLUGIN_ID, BranchStatus.WORKING));

        PluginViewExtend view = pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(icon, shot));

        assertEquals(PLUGIN_ID, view.getId());
        verify(pluginsRepository).setIcon(PLUGIN_ID, icon);
        verify(pluginsRepository).addScreenshots(PLUGIN_ID, List.of(shot));
    }

    @Test
    void commitAssets_screenshotOnly_doesNotTouchIcon() {
        String shot = "plugins/" + PLUGIN_ID + "/screenshots/a.png";
        when(s3Service.exists(anyString())).thenReturn(true);
        when(pluginsRepository.addScreenshots(PLUGIN_ID, List.of(shot))).thenReturn(samplePlugin);

        pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(shot));

        verify(pluginsRepository, never()).setIcon(any(), any());
    }

    @Test
    void commitAssets_keyOfAnotherPlugin_422() {
        String foreign = "plugins/" + UUID.randomUUID() + "/icon";

        ApiException ex = thrown(() -> pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(foreign)));

        assertEquals("invalid_key", ex.getCode());
        verify(pluginsRepository, never()).setIcon(any(), any());
    }

    @Test
    void commitAssets_blobKey_422() {
        String blob = "plugins/" + PLUGIN_ID + "/blobs/" + "a".repeat(64);

        assertEquals("invalid_key", thrown(() -> pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(blob))).getCode());
    }

    @Test
    void commitAssets_iconWithExtension_422() {
        String icon = "plugins/" + PLUGIN_ID + "/icon.png";

        assertEquals("invalid_key", thrown(() -> pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(icon))).getCode());
    }

    @Test
    void commitAssets_objectNotUploaded_422() {
        String shot = "plugins/" + PLUGIN_ID + "/screenshots/a.png";
        when(s3Service.exists(shot)).thenReturn(false);

        ApiException ex = thrown(() -> pluginsService.commitAssets(PLUGIN_ID, AUTHOR_ID, commit(shot)));

        assertEquals("object_not_found", ex.getCode());
        verify(pluginsRepository, never()).addScreenshots(any(), any());
    }

    @Test
    void commitAssets_nonAuthor_403() {
        String shot = "plugins/" + PLUGIN_ID + "/screenshots/a.png";

        assertEquals(403, thrown(() -> pluginsService.commitAssets(PLUGIN_ID, STRANGER_ID, commit(shot))).getStatus().value());
        verifyNoInteractions(s3Service);
    }

    @Test
    void deleteScreenshot_author_deletesFromS3AndRepository() {
        when(pluginsRepository.getScreenshotKey(PLUGIN_ID, SCREENSHOT_ID)).thenReturn("plugins/x/screenshots/a.png");

        pluginsService.deleteScreenshot(PLUGIN_ID, AUTHOR_ID, SCREENSHOT_ID);

        verify(s3Service).deleteScreenshot("plugins/x/screenshots/a.png");
        verify(pluginsRepository).deleteScreenshot(SCREENSHOT_ID);
    }

    @Test
    void deleteScreenshot_unknownScreenshot_404() {
        when(pluginsRepository.getScreenshotKey(PLUGIN_ID, SCREENSHOT_ID)).thenThrow(new EmptyResultDataAccessException(1));

        assertEquals("screenshot_not_found", thrown(() -> pluginsService.deleteScreenshot(PLUGIN_ID, AUTHOR_ID, SCREENSHOT_ID)).getCode());
    }

    @Test
    void deleteScreenshot_nonAuthor_403() {
        assertEquals(403, thrown(() -> pluginsService.deleteScreenshot(PLUGIN_ID, STRANGER_ID, SCREENSHOT_ID)).getStatus().value());
        verify(s3Service, never()).deleteScreenshot(any());
    }
}
