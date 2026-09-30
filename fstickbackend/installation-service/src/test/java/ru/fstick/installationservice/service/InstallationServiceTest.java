package ru.fstick.installationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.installationservice.client.IntegrationClient;
import ru.fstick.installationservice.client.RegistryClient;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.response.*;
import ru.fstick.installationservice.entity.Installation;
import ru.fstick.installationservice.exception.*;
import ru.fstick.installationservice.repository.InstallationRepository;
import ru.fstick.installationservice.store.PendingInstallStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

    @Mock private InstallationRepository repository;
    @Mock private IntegrationClient integrationClient;
    @Mock private RegistryClient registryClient;
    @Mock private PendingInstallStore pendingInstallStore;

    @InjectMocks
    private InstallationService service;

    private UUID pluginId;
    private UUID branchId;
    private UUID authorId;
    private String chatId;
    private UUID userId;
    private InstallRequest request;

    @BeforeEach
    void setUp() {
        pluginId  = UUID.randomUUID();
        branchId  = UUID.randomUUID();
        authorId  = UUID.randomUUID();
        chatId    = "!room:homeserver.org";
        userId    = UUID.randomUUID();

        request = new InstallRequest();
        request.setPluginId(pluginId);
        request.setBranchId(branchId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegistryClient.BranchView branch(UUID id, String status, String semver) {
        RegistryClient.BranchView b = new RegistryClient.BranchView();
        b.setId(id);
        b.setStatus(status);
        b.setSemver(semver);
        return b;
    }

    private RegistryClient.InternalPlugin plugin(RegistryClient.BranchView... branches) {
        RegistryClient.InternalPlugin plugin = new RegistryClient.InternalPlugin();
        plugin.setId(pluginId);
        plugin.setStatus("ACTIVE");
        plugin.setAuthorId(authorId);
        plugin.setBranches(new ArrayList<>(List.of(branches)));
        return plugin;
    }

    private Installation savedInstallation(UUID branch, String status) {
        Installation saved = new Installation();
        saved.setInstallationId(UUID.randomUUID());
        saved.setPluginId(pluginId);
        saved.setBranchId(branch);
        saved.setBranchStatus(status);
        saved.setPluginAuthorId(authorId);
        saved.setChatId(chatId);
        saved.setInstalledBy(userId);
        return saved;
    }

    private Installation savedInstallation() {
        return savedInstallation(branchId, "RELEASED");
    }

    private RegistryClient.BranchInfo lookup(UUID id, UUID owner, String status) {
        RegistryClient.BranchInfo info = new RegistryClient.BranchInfo();
        info.setBranchId(id);
        info.setPluginId(owner);
        info.setStatus(status);
        return info;
    }

    private void adminWithPlugin(RegistryClient.InternalPlugin plugin) {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(plugin);
        if (plugin != null) {
            plugin.getBranches().forEach(b -> lenient().when(registryClient.getBranch(b.getId()))
                    .thenReturn(lookup(b.getId(), pluginId, b.getStatus())));
        }
    }

    private ApiException assertApi(int status, String code, org.junit.jupiter.api.function.Executable call) {
        ApiException ex = assertThrows(ApiException.class, call);
        assertEquals(status, ex.getStatus().value());
        assertEquals(code, ex.getCode());
        return ex;
    }

    // ── install() ─────────────────────────────────────────────────────────────

    @Test
    void install_latestReleased_success() {
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.2.0")));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(repository.save(any())).thenReturn(savedInstallation());

        Object result = service.install(request, chatId, userId);

        assertInstanceOf(InstallationResponse.class, result);
        assertEquals(branchId, ((InstallationResponse) result).getBranchId());

        ArgumentCaptor<Installation> captor = ArgumentCaptor.forClass(Installation.class);
        verify(repository).save(captor.capture());
        assertEquals(branchId, captor.getValue().getBranchId());
        assertEquals("RELEASED", captor.getValue().getBranchStatus());
        assertEquals(authorId, captor.getValue().getPluginAuthorId());
        assertEquals(userId, captor.getValue().getInstalledBy());
        verify(integrationClient).notifyPluginInstalled(eq(userId), eq(pluginId), eq(chatId), eq(branchId), any());
        verify(integrationClient).pushInstallationChanged(eq(chatId), eq(pluginId), any(), eq(branchId));
    }

    @Test
    void install_olderReleased_returnsPendingWithWarning() {
        UUID latestId = UUID.randomUUID();
        adminWithPlugin(plugin(
                branch(branchId, "RELEASED", "1.9.0"),
                branch(latestId, "RELEASED", "1.10.0")));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(pendingInstallStore.save(any(), any(), any())).thenReturn("test-token");

        Object result = service.install(request, chatId, userId);

        assertInstanceOf(InstallationPendingResponse.class, result);
        InstallationPendingResponse pending = (InstallationPendingResponse) result;
        assertEquals("test-token", pending.getConfirmationToken());
        assertEquals("VERSION_OUTDATED", pending.getWarnings().get(0).getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void install_withoutBranch_picksHighestReleasedSemver() {
        UUID latestId = UUID.randomUUID();
        request.setBranchId(null);
        adminWithPlugin(plugin(
                branch(UUID.randomUUID(), "WORKING", "0.0.0"),
                branch(branchId, "RELEASED", "1.9.0"),
                branch(latestId, "RELEASED", "1.10.0"),
                branch(UUID.randomUUID(), "WAITING_APPROVE", "2.0.0")));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(repository.save(any())).thenReturn(savedInstallation(latestId, "RELEASED"));

        Object result = service.install(request, chatId, userId);

        assertInstanceOf(InstallationResponse.class, result);
        ArgumentCaptor<Installation> captor = ArgumentCaptor.forClass(Installation.class);
        verify(repository).save(captor.capture());
        assertEquals(latestId, captor.getValue().getBranchId());
    }

    @Test
    void install_withoutBranchAndNoRelease_throwsNoRelease() {
        request.setBranchId(null);
        adminWithPlugin(plugin(branch(branchId, "WORKING", "0.0.0")));

        assertApi(422, "no_release", () -> service.install(request, chatId, userId));
    }

    @Test
    void install_workingBranchByAuthor_success() {
        userId = authorId;
        adminWithPlugin(plugin(branch(branchId, "WORKING", "0.0.0")));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(repository.save(any())).thenReturn(savedInstallation(branchId, "WORKING"));

        Object result = service.install(request, chatId, userId);

        assertInstanceOf(InstallationResponse.class, result);
        ArgumentCaptor<Installation> captor = ArgumentCaptor.forClass(Installation.class);
        verify(repository).save(captor.capture());
        assertEquals("WORKING", captor.getValue().getBranchStatus());
    }

    @Test
    void install_workingBranchByNonAuthor_throwsDebugInstallForbidden() {
        adminWithPlugin(plugin(branch(branchId, "WORKING", "0.0.0")));

        assertApi(403, "debug_install_forbidden", () -> service.install(request, chatId, userId));
        verify(repository, never()).save(any());
    }

    @Test
    void install_nonInstallableStatuses_throwBranchNotFound() {
        for (String status : List.of("WAITING_APPROVE", "APPROVING", "REJECTED", "CANCELLED")) {
            reset(integrationClient, registryClient);
            adminWithPlugin(plugin(branch(branchId, status, "1.0.0")));

            assertApi(404, "branch_not_found", () -> service.install(request, chatId, userId));
        }
        verify(repository, never()).save(any());
    }

    @Test
    void install_branchOfOtherPlugin_throwsBranchNotFound() {
        adminWithPlugin(plugin(branch(UUID.randomUUID(), "RELEASED", "1.0.0")));
        when(registryClient.getBranch(branchId)).thenReturn(lookup(branchId, UUID.randomUUID(), "RELEASED"));

        assertApi(404, "branch_not_found", () -> service.install(request, chatId, userId));
    }

    @Test
    void install_unknownBranch_throwsBranchNotFound() {
        adminWithPlugin(plugin(branch(UUID.randomUUID(), "RELEASED", "1.0.0")));
        when(registryClient.getBranch(branchId)).thenReturn(null);

        assertApi(404, "branch_not_found", () -> service.install(request, chatId, userId));
    }

    @Test
    void install_userNotAdmin_throwsNotChatAdmin() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(false);

        assertApi(403, "not_chat_admin", () -> service.install(request, chatId, userId));

        verify(registryClient, never()).getPlugin(any());
    }

    @Test
    void install_pluginNotFound_throwsPluginNotFoundException() {
        adminWithPlugin(null);

        assertThrows(PluginNotFoundException.class,
                () -> service.install(request, chatId, userId));
    }

    @Test
    void install_pluginNotActive_throwsPluginNotFoundException() {
        RegistryClient.InternalPlugin plugin = plugin(branch(branchId, "RELEASED", "1.0.0"));
        plugin.setStatus("HIDDEN");
        adminWithPlugin(plugin);

        assertThrows(PluginNotFoundException.class,
                () -> service.install(request, chatId, userId));
    }

    @Test
    void install_alreadyInstalled_throwsConflict() {
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0")));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(true);

        assertThrows(PluginAlreadyInstalledException.class,
                () -> service.install(request, chatId, userId));
    }

    // ── confirmInstall() ──────────────────────────────────────────────────────

    @Test
    void confirmInstall_success() {
        PendingInstallStore.PendingInstall pending =
                new PendingInstallStore.PendingInstall(request, chatId, userId);

        when(pendingInstallStore.get("token")).thenReturn(pending);
        when(registryClient.getPlugin(pluginId))
                .thenReturn(plugin(branch(branchId, "RELEASED", "1.0.0"), branch(UUID.randomUUID(), "RELEASED", "1.1.0")));
        when(registryClient.getBranch(branchId)).thenReturn(lookup(branchId, pluginId, "RELEASED"));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(repository.save(any())).thenReturn(savedInstallation());

        InstallationResponse result = service.confirmInstall("token", userId);

        assertEquals(branchId, result.getBranchId());
        verify(pendingInstallStore).remove("token");
        verify(integrationClient).notifyPluginInstalled(any(), any(), any(), any(), any());
        verify(integrationClient).pushInstallationChanged(eq(chatId), eq(pluginId), any(), eq(branchId));
    }

    @Test
    void confirmInstall_invalidToken_throwsIllegalArgument() {
        when(pendingInstallStore.get("bad-token")).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.confirmInstall("bad-token", userId));
    }

    @Test
    void confirmInstall_wrongUser_throwsForbidden() {
        PendingInstallStore.PendingInstall pending =
                new PendingInstallStore.PendingInstall(request, chatId, UUID.randomUUID());

        when(pendingInstallStore.get("token")).thenReturn(pending);

        assertThrows(ForbiddenException.class,
                () -> service.confirmInstall("token", userId));
    }

    // ── changeBranch() ────────────────────────────────────────────────────────

    @Test
    void changeBranch_toReleased_success() {
        UUID newBranch = UUID.randomUUID();
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0"), branch(newBranch, "RELEASED", "1.1.0")));
        when(repository.updateBranch(installation.getInstallationId(), newBranch, "RELEASED"))
                .thenReturn(savedInstallation(newBranch, "RELEASED"));

        InstallationBranchResponse result =
                service.changeBranch(installation.getInstallationId(), newBranch, userId);

        assertEquals(newBranch, result.getBranchId());
        assertEquals("RELEASED", result.getBranchStatus());
        verify(integrationClient).pushInstallationChanged(eq(chatId), eq(pluginId), any(), eq(newBranch));
    }

    @Test
    void changeBranch_toWorkingByNonAuthor_throwsDebugInstallForbidden() {
        UUID devBranch = UUID.randomUUID();
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0"), branch(devBranch, "WORKING", "0.0.0")));

        assertApi(403, "debug_install_forbidden",
                () -> service.changeBranch(installation.getInstallationId(), devBranch, userId));
        verify(repository, never()).updateBranch(any(), any(), any());
    }

    @Test
    void changeBranch_toBranchOfOtherPlugin_throwsUnprocessable() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0")));
        UUID foreign = UUID.randomUUID();
        when(registryClient.getBranch(foreign)).thenReturn(lookup(foreign, UUID.randomUUID(), "RELEASED"));

        assertApi(422, "branch_of_other_plugin",
                () -> service.changeBranch(installation.getInstallationId(), foreign, userId));
    }

    @Test
    void changeBranch_unknownBranch_throwsBranchNotFound() {
        UUID unknown = UUID.randomUUID();
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0")));
        when(registryClient.getBranch(unknown)).thenReturn(null);

        assertApi(404, "branch_not_found",
                () -> service.changeBranch(installation.getInstallationId(), unknown, userId));
    }

    @Test
    void changeBranch_toRejectedBranch_throwsBranchNotFound() {
        UUID rejected = UUID.randomUUID();
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        adminWithPlugin(plugin(branch(branchId, "RELEASED", "1.0.0"), branch(rejected, "REJECTED", "1.1.0")));

        assertApi(404, "branch_not_found",
                () -> service.changeBranch(installation.getInstallationId(), rejected, userId));
    }

    @Test
    void changeBranch_userNotAdmin_throwsNotChatAdmin() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId())).thenReturn(Optional.of(installation));
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(false);

        assertApi(403, "not_chat_admin",
                () -> service.changeBranch(installation.getInstallationId(), branchId, userId));
        verify(registryClient, never()).getPlugin(any());
    }

    @Test
    void changeBranch_notFound_throwsInstallationNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFoundException.class,
                () -> service.changeBranch(id, branchId, userId));
    }

    // ── uninstall() ───────────────────────────────────────────────────────────

    @Test
    void uninstall_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFoundException.class,
                () -> service.uninstall(id, userId));
    }

    @Test
    void uninstall_userNotAdmin_throwsNotChatAdmin() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(false);

        assertApi(403, "not_chat_admin",
                () -> service.uninstall(installation.getInstallationId(), userId));

        verify(repository, never()).deleteById(any());
    }

    @Test
    void uninstall_success() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);

        service.uninstall(installation.getInstallationId(), userId);

        verify(repository).deleteById(installation.getInstallationId());
        verify(integrationClient).notifyPluginUninstalled(any(), any(), any(), any());
        verify(integrationClient).pushInstallationChanged(
                chatId, pluginId, installation.getInstallationId(), null);
    }

    // ── getAllByChatId() ───────────────────────────────────────────────────────

    @Test
    void getAllByChatId_success() {
        Installation installation = savedInstallation(branchId, "WORKING");
        when(integrationClient.isMember(userId, chatId)).thenReturn(true);
        when(repository.findAllByChatId(chatId, 20, 0)).thenReturn(List.of(installation));
        when(repository.countByChatId(chatId)).thenReturn(1);

        PaginatedResponse<InstallationShortResponse> result =
                service.getAllByChatId(chatId, userId, 1, 20);

        assertEquals(1, result.getTotal());
        InstallationShortResponse item = result.getItems().get(0);
        assertEquals(branchId, item.getBranchId());
        assertEquals("WORKING", item.getBranchStatus());
        assertEquals(authorId, item.getAuthorId());
    }

    @Test
    void getAllByChatId_userNotMember_throwsForbidden() {
        when(integrationClient.isMember(userId, chatId)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> service.getAllByChatId(chatId, userId, 1, 20));
    }

    // ── getById() ─────────────────────────────────────────────────────────────

    @Test
    void getById_success() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(integrationClient.isMember(userId, chatId)).thenReturn(true);

        InstallationDetailsResponse result = service.getById(installation.getInstallationId(), userId);

        assertEquals(installation.getInstallationId(), result.getInstallationId());
        assertEquals(branchId, result.getBranchId());
    }

    @Test
    void getById_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFoundException.class,
                () -> service.getById(id, userId));
    }

    // ── internal ──────────────────────────────────────────────────────────────

    @Test
    void resolve_returnsInstalledBranch() {
        when(repository.findByPluginIdAndChatId(pluginId, chatId))
                .thenReturn(Optional.of(savedInstallation(branchId, "WORKING")));

        ResolveResponse result = service.resolve(pluginId, chatId);

        assertEquals(branchId, result.getBranchId());
        assertEquals("WORKING", result.getBranchStatus());
        verifyNoInteractions(registryClient);
    }

    @Test
    void resolve_notInstalled_throwsNotInstalled() {
        when(repository.findByPluginIdAndChatId(pluginId, chatId)).thenReturn(Optional.empty());

        assertApi(404, "not_installed", () -> service.resolve(pluginId, chatId));
    }

    @Test
    void chatsForBranch_returnsAllChats() {
        when(repository.findChatIdsByBranchId(branchId)).thenReturn(List.of("!a:x", "!b:x"));

        assertEquals(List.of("!a:x", "!b:x"), service.chatsForBranch(branchId).getChatIds());
    }
}
