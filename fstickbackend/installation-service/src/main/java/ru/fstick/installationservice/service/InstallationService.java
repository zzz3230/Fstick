package ru.fstick.installationservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.fstick.installationservice.client.IntegrationClient;
import ru.fstick.installationservice.client.RegistryClient;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.response.*;
import ru.fstick.installationservice.entity.Installation;
import ru.fstick.installationservice.exception.*;
import ru.fstick.installationservice.repository.InstallationRepository;
import ru.fstick.installationservice.store.PendingInstallStore;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InstallationService {

    private static final String RELEASED = "RELEASED";
    private static final String WORKING = "WORKING";

    private final InstallationRepository repository;
    private final IntegrationClient integrationClient;
    private final RegistryClient registryClient;
    private final PendingInstallStore pendingInstallStore;

    public InstallationService(InstallationRepository repository,
                               IntegrationClient integrationClient,
                               RegistryClient registryClient,
                               PendingInstallStore pendingInstallStore) {
        this.repository = repository;
        this.integrationClient = integrationClient;
        this.registryClient = registryClient;
        this.pendingInstallStore = pendingInstallStore;
    }

    public Object install(InstallRequest request, String chatId, UUID userId) {
        checkChatAdmin(userId, chatId);

        InstallTarget target = resolveTarget(request.getPluginId(), request.getBranchId(), userId);
        checkNotInstalled(request.getPluginId(), chatId);

        List<InstallWarning> warnings = outdatedWarnings(target);
        if (!warnings.isEmpty()) {
            InstallRequest pinned = new InstallRequest();
            pinned.setPluginId(request.getPluginId());
            pinned.setBranchId(target.branch().getId());
            String token = pendingInstallStore.save(pinned, chatId, userId);
            return new InstallationPendingResponse(token, warnings);
        }

        return doInstall(target, chatId, userId);
    }

    public InstallationResponse confirmInstall(String token, UUID userId) {
        PendingInstallStore.PendingInstall pending = pendingInstallStore.get(token);

        if (pending == null) {
            throw new IllegalArgumentException("Invalid or expired confirmation token");
        }

        if (!pending.userId().equals(userId)) {
            throw new ForbiddenException(userId, pending.chatId());
        }

        pendingInstallStore.remove(token);

        InstallRequest request = pending.request();
        InstallTarget target = resolveTarget(request.getPluginId(), request.getBranchId(), userId);
        checkNotInstalled(request.getPluginId(), pending.chatId());

        return doInstall(target, pending.chatId(), userId);
    }

    private InstallationResponse doInstall(InstallTarget target, String chatId, UUID userId) {
        Installation installation = new Installation();
        installation.setPluginId(target.plugin().getId());
        installation.setBranchId(target.branch().getId());
        installation.setBranchStatus(target.branch().getStatus());
        installation.setPluginAuthorId(target.plugin().getAuthorId());
        installation.setChatId(chatId);
        installation.setInstalledBy(userId);

        Installation saved = repository.save(installation);

        integrationClient.notifyPluginInstalled(
                userId, saved.getPluginId(), chatId,
                saved.getBranchId(), saved.getInstallationId()
        );
        integrationClient.pushInstallationChanged(
                chatId, saved.getPluginId(), saved.getInstallationId(), saved.getBranchId()
        );

        return new InstallationResponse(
                saved.getInstallationId(), saved.getPluginId(), saved.getBranchId(),
                saved.getBranchStatus(), saved.getInstalledBy(), saved.getInstalledAt()
        );
    }

    public InstallationBranchResponse changeBranch(UUID installationId, UUID branchId, UUID userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatAdmin(userId, installation.getChatId());

        RegistryClient.InternalPlugin plugin = loadActivePlugin(installation.getPluginId());
        RegistryClient.BranchInfo info = registryClient.getBranch(branchId);
        if (info == null) {
            throw branchNotFound(branchId);
        }
        if (!plugin.getId().equals(info.getPluginId())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "branch_of_other_plugin",
                    "Branch " + branchId + " does not belong to plugin " + plugin.getId());
        }
        RegistryClient.BranchView branch = toView(info);
        checkInstallable(plugin, branch, userId);

        Installation updated = repository.updateBranch(installationId, branch.getId(), branch.getStatus());

        integrationClient.pushInstallationChanged(
                updated.getChatId(), updated.getPluginId(),
                updated.getInstallationId(), updated.getBranchId()
        );

        return new InstallationBranchResponse(
                updated.getInstallationId(), updated.getBranchId(), updated.getBranchStatus()
        );
    }

    public PaginatedResponse<InstallationShortResponse> getAllByChatId(String chatId,
                                                                       UUID userId,
                                                                       int page, int limit) {
        checkChatMembership(userId, chatId);

        int offset = (page - 1) * limit;
        List<Installation> installations = repository.findAllByChatId(chatId, limit, offset);
        int totalCount = repository.countByChatId(chatId);

        List<InstallationShortResponse> data = installations.stream()
                .map(this::toShortResponse)
                .toList();

        return new PaginatedResponse<>(data, page, limit, totalCount);
    }

    public InstallationDetailsResponse getById(UUID installationId, UUID userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        return toResponse(installation);
    }

    public void uninstall(UUID installationId, UUID userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatAdmin(userId, installation.getChatId());

        repository.deleteById(installationId);

        integrationClient.notifyPluginUninstalled(
                userId,
                installation.getPluginId(),
                installation.getChatId(),
                installation.getInstallationId()
        );
        integrationClient.pushInstallationChanged(
                installation.getChatId(), installation.getPluginId(),
                installation.getInstallationId(), null
        );
    }

    public ResolveResponse resolve(UUID pluginId, String chatId) {
        Installation installation = repository.findByPluginIdAndChatId(pluginId, chatId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_installed",
                        "Plugin " + pluginId + " is not installed in chat " + chatId));

        return new ResolveResponse(installation.getBranchId(), installation.getBranchStatus());
    }

    public BranchChatsResponse chatsForBranch(UUID branchId) {
        return new BranchChatsResponse(repository.findChatIdsByBranchId(branchId));
    }

    private void checkChatMembership(UUID userId, String chatId) {
        if (!integrationClient.isMember(userId, chatId)) {
            throw new ForbiddenException(userId, chatId);
        }
    }

    private void checkChatAdmin(UUID userId, String chatId) {
        if (!integrationClient.isAdmin(userId, chatId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_chat_admin",
                    "User " + userId + " is not an admin of chat " + chatId);
        }
    }

    private void checkNotInstalled(UUID pluginId, String chatId) {
        if (repository.existsByPluginIdAndChatId(pluginId, chatId)) {
            throw new PluginAlreadyInstalledException(pluginId.toString(), chatId);
        }
    }

    private InstallTarget resolveTarget(UUID pluginId, UUID branchId, UUID userId) {
        RegistryClient.InternalPlugin plugin = loadActivePlugin(pluginId);
        RegistryClient.BranchView latest = highestReleased(plugin).orElse(null);

        RegistryClient.BranchView branch;
        if (branchId == null) {
            if (latest == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "no_release",
                        "Plugin " + pluginId + " has no released version");
            }
            branch = latest;
        } else {
            RegistryClient.BranchInfo info = registryClient.getBranch(branchId);
            if (info == null || !pluginId.equals(info.getPluginId())) {
                throw branchNotFound(branchId);
            }
            branch = toView(info);
        }

        checkInstallable(plugin, branch, userId);
        return new InstallTarget(plugin, branch, latest);
    }

    private RegistryClient.InternalPlugin loadActivePlugin(UUID pluginId) {
        RegistryClient.InternalPlugin plugin = registryClient.getPlugin(pluginId);

        if (plugin == null || !"ACTIVE".equals(plugin.getStatus())) {
            throw new PluginNotFoundException(pluginId);
        }
        return plugin;
    }

    private void checkInstallable(RegistryClient.InternalPlugin plugin,
                                  RegistryClient.BranchView branch, UUID userId) {
        if (RELEASED.equals(branch.getStatus())) {
            return;
        }
        if (!WORKING.equals(branch.getStatus())) {
            throw branchNotFound(branch.getId());
        }
        if (!userId.equals(plugin.getAuthorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "debug_install_forbidden",
                    "Only the plugin author can install the working branch");
        }
    }

    private RegistryClient.BranchView toView(RegistryClient.BranchInfo info) {
        RegistryClient.BranchView view = new RegistryClient.BranchView();
        view.setId(info.getBranchId());
        view.setStatus(info.getStatus());
        view.setSemver(info.getSemver());
        return view;
    }

    private Optional<RegistryClient.BranchView> highestReleased(RegistryClient.InternalPlugin plugin) {
        return plugin.getBranches().stream()
                .filter(b -> RELEASED.equals(b.getStatus()))
                .max(Comparator.comparing(b -> parseSemver(b.getSemver()), InstallationService::compareSemver));
    }

    private static int[] parseSemver(String semver) {
        String[] parts = semver.split("\\.");
        return new int[]{
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }

    private static int compareSemver(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    private List<InstallWarning> outdatedWarnings(InstallTarget target) {
        RegistryClient.BranchView latest = target.latestRelease();
        if (RELEASED.equals(target.branch().getStatus())
                && !latest.getId().equals(target.branch().getId())) {
            return List.of(new InstallWarning(
                    "VERSION_OUTDATED",
                    "Устанавливается не последняя версия. Последняя: " + latest.getSemver()
            ));
        }
        return List.of();
    }

    private ApiException branchNotFound(UUID branchId) {
        return new ApiException(HttpStatus.NOT_FOUND, "branch_not_found",
                "Branch " + branchId + " not found");
    }

    private InstallationDetailsResponse toResponse(Installation i) {
        return new InstallationDetailsResponse(
                i.getInstallationId(), i.getPluginId(), i.getBranchId(), i.getBranchStatus(),
                i.getPluginAuthorId(), i.getChatId(), i.getInstalledBy(),
                i.getInstalledAt(), i.getUpdatedAt()
        );
    }

    private InstallationShortResponse toShortResponse(Installation i) {
        return new InstallationShortResponse(
                i.getInstallationId(), i.getPluginId(), i.getBranchId(), i.getBranchStatus(),
                i.getPluginAuthorId(), i.getInstalledBy(), i.getInstalledAt()
        );
    }

    private record InstallTarget(RegistryClient.InternalPlugin plugin,
                                 RegistryClient.BranchView branch,
                                 RegistryClient.BranchView latestRelease) {}
}
