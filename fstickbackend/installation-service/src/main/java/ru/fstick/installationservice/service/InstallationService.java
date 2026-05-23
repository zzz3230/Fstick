package ru.fstick.installationservice.service;

import org.springframework.stereotype.Service;
import ru.fstick.installationservice.client.IntegrationClient;
import ru.fstick.installationservice.client.RegistryClient;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.request.UpdateInstallationRequest;
import ru.fstick.installationservice.dto.response.*;
import ru.fstick.installationservice.entity.Installation;
import ru.fstick.installationservice.exception.*;
import ru.fstick.installationservice.repository.InstallationRepository;

import java.util.List;
import java.util.UUID;

@Service
public class InstallationService {

    private final InstallationRepository repository;
    private final IntegrationClient integrationClient;
    private final RegistryClient registryClient;

    public InstallationService(InstallationRepository repository,
                               IntegrationClient integrationClient,
                               RegistryClient registryClient) {
        this.repository = repository;
        this.integrationClient = integrationClient;
        this.registryClient = registryClient;
    }

    public InstallationWithWarningsResponse install(InstallRequest request,
                                                    String chatId, String userId) {
        checkChatMembership(userId, chatId);

        List<InstallWarning> warnings = checkPluginAndVersion(
                request.getPluginId(), request.getVersionId()
        );

        if (repository.existsByPluginIdAndChatId(request.getPluginId(), chatId)) {
            throw new PluginAlreadyInstalledException(request.getPluginId().toString(), chatId);
        }

        Installation installation = new Installation();
        installation.setPluginId(request.getPluginId());
        installation.setVersionId(request.getVersionId());
        installation.setChatId(chatId);
        installation.setInstalledBy(userId);

        Installation saved = repository.save(installation);

        integrationClient.notifyPluginInstalled(
                userId,
                saved.getPluginId(),
                chatId,
                saved.getVersionId(),
                saved.getInstallationId()
        );

        return new InstallationWithWarningsResponse(toResponse(saved), warnings);
    }

    public PaginatedResponse<InstallationShortResponse> getAllByChatId(String chatId,
                                                                       String userId,
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

    public InstallationResponse getById(UUID installationId, String userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        return toResponse(installation);
    }

    public void uninstall(UUID installationId, String userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        repository.deleteById(installationId);

        integrationClient.notifyPluginUninstalled(
                userId,
                installation.getPluginId(),
                installation.getChatId(),
                installation.getInstallationId()
        );
    }

    private void checkChatMembership(String userId, String chatId) {
        if (!integrationClient.isMember(userId, chatId)) {
            throw new ForbiddenException(userId, chatId);
        }
    }

    private List<InstallWarning> checkPluginAndVersion(UUID pluginId, UUID versionId) {
        RegistryClient.PluginViewExtend plugin = registryClient.getPlugin(pluginId);

        if (plugin == null || !"ACTIVE".equals(plugin.getStatus())) {
            throw new PluginNotFoundException(pluginId);
        }

        List<RegistryClient.VersionView> versions = plugin.getVersions();

        boolean versionExists = versions.stream()
                .anyMatch(v -> versionId.equals(v.getVersionId()));

        if (!versionExists) {
            throw new VersionNotFoundException(versionId, pluginId);
        }

        // versions.get(0) — последняя версия (Registry отдаёт в порядке DESC)
        UUID latestVersionId = versions.get(0).getVersionId();
        if (!versionId.equals(latestVersionId)) {
            return List.of(new InstallWarning(
                    "VERSION_OUTDATED",
                    "Устанавливается не последняя версия. Последняя: "
                            + versions.get(0).getVersion()
            ));
        }

        return List.of();
    }

    private InstallationResponse toResponse(Installation i) {
        return new InstallationResponse(
                i.getInstallationId(), i.getPluginId(), i.getVersionId(),
                i.getChatId(), i.getInstalledBy(), i.getInstalledAt(), i.getUpdatedAt()
        );
    }

    private InstallationShortResponse toShortResponse(Installation i) {
        return new InstallationShortResponse(
                i.getInstallationId(), i.getPluginId(), i.getVersionId(),
                i.getInstalledBy(), i.getInstalledAt()
        );
    }
}