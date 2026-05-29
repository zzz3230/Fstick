package ru.fstick.installationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private UUID versionId;
    private String chatId;
    private String userId;
    private InstallRequest request;

    @BeforeEach
    void setUp() {
        pluginId  = UUID.randomUUID();
        versionId = UUID.randomUUID();
        chatId    = "!room:homeserver.org";
        userId    = "@user:homeserver.org";

        request = new InstallRequest();
        request.setPluginId(pluginId);
        request.setVersionId(versionId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RegistryClient.PluginViewExtend activePlugin(UUID... versionIds) {
        RegistryClient.PluginViewExtend plugin = new RegistryClient.PluginViewExtend();
        plugin.setStatus("ACTIVE");
        List<RegistryClient.VersionView> versions = new java.util.ArrayList<>();
        for (UUID id : versionIds) {
            RegistryClient.VersionView v = new RegistryClient.VersionView();
            v.setVersionId(id);
            v.setVersion("1.0." + versions.size());
            versions.add(v);
        }
        plugin.setVersions(versions);
        return plugin;
    }

    private Installation savedInstallation() {
        Installation saved = new Installation();
        saved.setInstallationId(UUID.randomUUID());
        saved.setPluginId(pluginId);
        saved.setVersionId(versionId);
        saved.setChatId(chatId);
        saved.setInstalledBy(userId);
        return saved;
    }

    // ── install() ─────────────────────────────────────────────────────────────

    @Test
    void install_success() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(activePlugin(versionId));
        when(repository.existsByPluginIdAndChatId(pluginId, chatId)).thenReturn(false);
        when(repository.save(any())).thenReturn(savedInstallation());

        Object result = service.install(request, chatId, userId);

        assertInstanceOf(InstallationWithWarningsResponse.class, result);
        assertTrue(((InstallationWithWarningsResponse) result).getWarnings().isEmpty());
        verify(integrationClient).notifyPluginInstalled(any(), any(), any(), any(), any());
    }

    @Test
    void install_outdatedVersion_returnsPendingToken() {
        UUID latestId = UUID.randomUUID();
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(activePlugin(latestId, versionId));
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
    void install_userNotAdmin_throwsForbidden() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> service.install(request, chatId, userId));

        verify(registryClient, never()).getPlugin(any());
    }

    @Test
    void install_pluginNotFound_throwsPluginNotFoundException() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(null);

        assertThrows(PluginNotFoundException.class,
                () -> service.install(request, chatId, userId));
    }

    @Test
    void install_pluginNotActive_throwsPluginNotFoundException() {
        RegistryClient.PluginViewExtend plugin = new RegistryClient.PluginViewExtend();
        plugin.setStatus("ARCHIVED");
        plugin.setVersions(List.of());

        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(plugin);

        assertThrows(PluginNotFoundException.class,
                () -> service.install(request, chatId, userId));
    }

    @Test
    void install_versionNotFound_throwsVersionNotFoundException() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(activePlugin(UUID.randomUUID()));

        assertThrows(VersionNotFoundException.class,
                () -> service.install(request, chatId, userId));
    }

    @Test
    void install_alreadyInstalled_throwsConflict() {
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(true);
        when(registryClient.getPlugin(pluginId)).thenReturn(activePlugin(versionId));
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
        when(repository.save(any())).thenReturn(savedInstallation());

        InstallationWithWarningsResponse result = service.confirmInstall("token", userId);

        assertNotNull(result.getInstallation());
        verify(pendingInstallStore).remove("token");
        verify(integrationClient).notifyPluginInstalled(any(), any(), any(), any(), any());
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
                new PendingInstallStore.PendingInstall(request, chatId, "@other:homeserver.org");

        when(pendingInstallStore.get("token")).thenReturn(pending);

        assertThrows(ForbiddenException.class,
                () -> service.confirmInstall("token", userId));
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
    void uninstall_userNotAdmin_throwsForbidden() {
        Installation installation = savedInstallation();
        when(repository.findById(installation.getInstallationId()))
                .thenReturn(Optional.of(installation));
        when(integrationClient.isAdmin(userId, chatId)).thenReturn(false);

        assertThrows(ForbiddenException.class,
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
    }

    // ── getAllByChatId() ───────────────────────────────────────────────────────

    @Test
    void getAllByChatId_success() {
        when(integrationClient.isMember(userId, chatId)).thenReturn(true);
        when(repository.findAllByChatId(chatId, 20, 0)).thenReturn(List.of());
        when(repository.countByChatId(chatId)).thenReturn(0);

        PaginatedResponse<InstallationShortResponse> result =
                service.getAllByChatId(chatId, userId, 1, 20);

        assertEquals(0, result.getTotalCount());
        assertTrue(result.getData().isEmpty());
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

        InstallationResponse result = service.getById(installation.getInstallationId(), userId);

        assertEquals(installation.getInstallationId(), result.getInstallationId());
    }

    @Test
    void getById_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(InstallationNotFoundException.class,
                () -> service.getById(id, userId));
    }
}