package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.client.RuntimeClient;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.api.editor.EditResponse;
import ru.fstick.registry_service.dto.api.editor.ReloadResponse;
import ru.fstick.registry_service.dto.api.editor.SaveCodeRequest;
import ru.fstick.registry_service.dto.api.editor.SaveCodeResponse;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.BranchRepository;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static ru.fstick.registry_service.TestData.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EditorServiceTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final String CHAT = "!room:example.org";
    private static final String CLIENT_SHA = "sha256:" + CLIENT_HEX;
    private static final String SERVER_SHA = "sha256:" + SERVER_HEX;

    @Mock private PluginsRepository pluginsRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BlobStore blobStore;
    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;
    @Mock private ModeratorService moderatorService;
    @Mock private RuntimeClient runtimeClient;
    @Mock private ReloadNotifier reloadNotifier;
    @Mock private TransactionTemplate transactionTemplate;

    private EditorService service;
    private Branch dev;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        AccessGuard guard = new AccessGuard(pluginsRepository, installationClient, integrationClient, moderatorService);
        service = new EditorService(pluginsRepository, branchRepository, blobStore, guard, runtimeClient,
                reloadNotifier, new LuaSyntaxChecker(), transactionTemplate);

        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));

        PluginData plugin = plugin(PLUGIN_ID, AUTHOR);
        when(pluginsRepository.findPlugin(PLUGIN_ID)).thenReturn(Optional.of(plugin));
        dev = branch(PLUGIN_ID, BranchStatus.WORKING);
        registerBranch(dev);
        when(blobStore.get(PLUGIN_ID.toString(), CLIENT_HEX)).thenReturn("js source");
        when(blobStore.get(PLUGIN_ID.toString(), SERVER_HEX)).thenReturn("lua source");
        when(runtimeClient.reload(any(), any(), any())).thenReturn(new RuntimeClient.ReloadResult(true, null));
    }

    private void registerBranch(Branch branch) {
        when(branchRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchRepository.lockForUpdate(branch.getId())).thenReturn(Optional.of(branch));
    }

    private static SaveCodeRequest.Side side(String text, String baseSha) {
        return new SaveCodeRequest.Side(text, baseSha);
    }

    private SaveCodeResponse save(SaveCodeRequest request) {
        return service.save(PLUGIN_ID, dev.getId(), AUTHOR, CHAT, request);
    }

    private static ApiException thrown(Runnable action) {
        return assertThrows(ApiException.class, action::run);
    }

    @Test
    void edit_returnsBothSourcesAndEchoesChat() {
        EditResponse response = service.edit(PLUGIN_ID, dev.getId(), AUTHOR, CHAT);

        assertEquals("WORKING", response.branch().status());
        assertEquals("cl.js@1.0.0", response.branch().runtime().client());
        assertEquals("js source", response.source().client().getText());
        assertEquals(CLIENT_SHA, response.source().client().getSha());
        assertEquals(SERVER_SHA, response.source().server().getSha());
        assertEquals(CHAT, response.chatId());
        assertEquals(AUTHOR, response.plugin().authorId());
    }

    @Test
    void edit_nonAuthorWhoCannotSeeBranch_is404() {
        ApiException ex = thrown(() -> service.edit(PLUGIN_ID, dev.getId(), STRANGER, CHAT));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void edit_nonAuthorWithChatAccess_is403() {
        when(installationClient.resolve(PLUGIN_ID, CHAT))
                .thenReturn(Optional.of(new InstallationClient.Resolved(dev.getId(), "WORKING")));
        when(integrationClient.isMember(CHAT, STRANGER)).thenReturn(true);

        ApiException ex = thrown(() -> service.edit(PLUGIN_ID, dev.getId(), STRANGER, CHAT));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("not_author", ex.getCode());
    }

    @Test
    void edit_releasedBranch_is409NotDevBranch() {
        Branch released = branch(PLUGIN_ID, BranchStatus.RELEASED);
        registerBranch(released);

        ApiException ex = thrown(() -> service.edit(PLUGIN_ID, released.getId(), AUTHOR, CHAT));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("not_dev_branch", ex.getCode());
    }

    @Test
    void edit_branchOfAnotherPlugin_is404() {
        Branch foreign = branch(UUID.randomUUID(), BranchStatus.WORKING);
        registerBranch(foreign);

        ApiException ex = thrown(() -> service.edit(PLUGIN_ID, foreign.getId(), AUTHOR, CHAT));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void save_clientOnly_noRuntimeCall_notifiesAndReportsClientChanged() {
        SaveCodeResponse response = save(new SaveCodeRequest(side("new js", CLIENT_SHA), null, null));

        assertTrue(response.clientChanged());
        assertTrue(response.reloaded());
        assertNull(response.retryAfterMs());
        assertEquals(SERVER_SHA, response.sha().server());
        assertEquals("sha256:" + BlobStore.hex("new js"), response.sha().client());
        verify(blobStore).put(PLUGIN_ID.toString(), "new js");
        verify(branchRepository).updateShas(dev.getId(), BlobStore.hex("new js"), SERVER_HEX);
        verify(runtimeClient, never()).reload(any(), any(), any());
        verify(reloadNotifier).clientChanged(PLUGIN_ID, dev.getId());
    }

    @Test
    void save_serverOnly_reloadsRuntimeOnce_andDoesNotNotify() {
        SaveCodeResponse response = save(new SaveCodeRequest(null, side("return 1", SERVER_SHA), null));

        assertFalse(response.clientChanged());
        assertTrue(response.reloaded());
        verify(runtimeClient, times(1)).reload(PLUGIN_ID, CHAT, dev.getId());
        verifyNoInteractions(reloadNotifier);
    }

    @Test
    void save_serverOnly_throttled_reportsRetryAfter() {
        when(runtimeClient.reload(any(), any(), any())).thenReturn(new RuntimeClient.ReloadResult(false, 2100L));

        SaveCodeResponse response = save(new SaveCodeRequest(null, side("return 1", SERVER_SHA), null));

        assertFalse(response.reloaded());
        assertEquals(2100L, response.retryAfterMs());
        verifyNoInteractions(reloadNotifier);
    }

    @Test
    void save_runtimeDown_stillSavesWithWarning() {
        when(runtimeClient.reload(any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "runtime_unavailable", "down"));

        SaveCodeResponse response = save(new SaveCodeRequest(null, side("return 1", SERVER_SHA), null));

        assertFalse(response.reloaded());
        assertEquals(0L, response.retryAfterMs());
        assertEquals("runtime unavailable, retry hot-reload", response.warnings().get(0).message());
        verify(branchRepository).updateShas(eq(dev.getId()), eq(CLIENT_HEX), anyString());
    }

    @Test
    void save_bothSides_oneStale_is409_andNothingWritten() {
        SaveCodeRequest request = new SaveCodeRequest(
                side("new js", CLIENT_SHA), side("new lua", "sha256:" + "a".repeat(64)), null);

        ApiException ex = thrown(() -> save(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("stale_code", ex.getCode());
        assertEquals(Map.of("client", CLIENT_SHA, "server", SERVER_SHA), ex.getExtra().get("current"));
        verify(blobStore, never()).put(anyString(), anyString());
        verify(branchRepository, never()).updateShas(any(), anyString(), anyString());
        verifyNoInteractions(runtimeClient, reloadNotifier);
    }

    @Test
    void save_serverOverLimit_is413WithSide() {
        String text = "x".repeat(500_001);

        ApiException ex = thrown(() -> save(new SaveCodeRequest(null, side(text, SERVER_SHA), null)));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
        assertEquals("file_too_large", ex.getCode());
        assertEquals("server", ex.getExtra().get("side"));
        assertEquals(500_000, ex.getExtra().get("limit"));
        assertEquals(500_001, ex.getExtra().get("actual"));
        verify(blobStore, never()).put(anyString(), anyString());
    }

    @Test
    void save_cyrillicAtByteLimit_isAccepted() {
        String text = "ж".repeat(250_000);

        SaveCodeResponse response = save(new SaveCodeRequest(side(text, CLIENT_SHA), null, null));

        assertTrue(response.clientChanged());
        verify(blobStore).put(PLUGIN_ID.toString(), text);
    }

    @Test
    void save_identicalText_writesNothingAndDoesNotReload() {
        when(blobStore.get(any(), any())).thenReturn("");
        Branch same = branch(PLUGIN_ID, BranchStatus.WORKING);
        same.setClientBlobSha(BlobStore.hex("js"));
        same.setServerBlobSha(BlobStore.hex("lua"));
        registerBranch(same);
        dev = same;

        SaveCodeResponse response = save(new SaveCodeRequest(
                side("js", "sha256:" + BlobStore.hex("js")), side("lua", "sha256:" + BlobStore.hex("lua")), null));

        assertFalse(response.clientChanged());
        assertTrue(response.reloaded());
        verify(blobStore, never()).put(anyString(), anyString());
        verify(branchRepository, never()).updateShas(any(), anyString(), anyString());
        verifyNoInteractions(runtimeClient, reloadNotifier);
    }

    @Test
    void save_luaSyntaxError_savedWithLineWarning() {
        SaveCodeResponse response = save(new SaveCodeRequest(null, side("local a = 1\nlocal = = 2", SERVER_SHA), null));

        assertEquals(1, response.warnings().size());
        assertEquals("server", response.warnings().get(0).side());
        assertEquals(2, response.warnings().get(0).line());
        verify(blobStore).put(eq(PLUGIN_ID.toString()), anyString());
    }

    @Test
    void save_nonAuthor_is404() {
        ApiException ex = thrown(() -> service.save(PLUGIN_ID, dev.getId(), STRANGER, CHAT,
                new SaveCodeRequest(side("x", CLIENT_SHA), null, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(blobStore, never()).put(anyString(), anyString());
    }

    @Test
    void save_releasedBranch_is409NotDevBranch() {
        Branch released = branch(PLUGIN_ID, BranchStatus.RELEASED);
        registerBranch(released);

        ApiException ex = thrown(() -> service.save(PLUGIN_ID, released.getId(), AUTHOR, CHAT,
                new SaveCodeRequest(side("x", CLIENT_SHA), null, null)));

        assertEquals("not_dev_branch", ex.getCode());
    }

    @Test
    void save_runtimeKey_is422() {
        SaveCodeRequest request = new SaveCodeRequest(null, null, Map.of("server", "sv.lua@2.0.0"));

        ApiException ex = thrown(() -> save(request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("runtime_change_unsupported", ex.getCode());
    }

    @Test
    void save_emptyRequest_is422() {
        ApiException ex = thrown(() -> save(new SaveCodeRequest(null, null, null)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void reload_success() {
        ReloadResponse response = service.reload(PLUGIN_ID, dev.getId(), AUTHOR, CHAT);

        assertTrue(response.reloaded());
        verify(runtimeClient).reload(PLUGIN_ID, CHAT, dev.getId());
    }

    @Test
    void reload_throttled_is429WithRetryAfter() {
        when(runtimeClient.reload(any(), any(), any())).thenReturn(new RuntimeClient.ReloadResult(false, 1500L));

        ApiException ex = thrown(() -> service.reload(PLUGIN_ID, dev.getId(), AUTHOR, CHAT));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        assertEquals("reload_throttled", ex.getCode());
        assertEquals(1500L, ex.getExtra().get("retry_after_ms"));
    }

    @Test
    void reload_runtimeDown_is503() {
        when(runtimeClient.reload(any(), any(), any())).thenThrow(
                new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "runtime_unavailable", "down"));

        ApiException ex = thrown(() -> service.reload(PLUGIN_ID, dev.getId(), AUTHOR, CHAT));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void reload_nonAuthor_neverCallsRuntime() {
        thrown(() -> service.reload(PLUGIN_ID, dev.getId(), STRANGER, CHAT));

        verifyNoInteractions(runtimeClient);
    }
}
