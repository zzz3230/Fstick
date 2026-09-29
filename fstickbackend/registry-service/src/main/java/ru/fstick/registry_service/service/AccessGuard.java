package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.PluginsRepository;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final PluginsRepository pluginsRepository;
    private final InstallationClient installationClient;
    private final IntegrationClient integrationClient;

    public PluginData requireAuthor(UUID pluginId, UUID caller) {
        PluginData plugin = pluginsRepository.findPlugin(pluginId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found: " + pluginId));
        if (!isAuthor(plugin, caller)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_author", "Only the plugin author can do this");
        }
        return plugin;
    }

    public boolean isAuthor(PluginData plugin, UUID caller) {
        return caller != null && caller.equals(plugin.getAuthorId());
    }

    public boolean isModerator(UUID caller) {
        return false;
    }

    public boolean canReadBranch(UUID caller, PluginData plugin, Branch branch, String chatId) {
        return switch (branch.getStatus()) {
            case RELEASED -> true;
            case WAITING_APPROVE, APPROVING -> isAuthor(plugin, caller) || isModerator(caller);
            case WORKING -> isAuthor(plugin, caller) || chatGrantsAccess(caller, plugin, branch, chatId);
            case REJECTED, CANCELLED -> isAuthor(plugin, caller);
        };
    }

    public List<Branch> visibleBranches(UUID caller, PluginData plugin, List<Branch> branches, String chatId) {
        return branches.stream()
                .filter(branch -> canReadBranch(caller, plugin, branch, chatId))
                .toList();
    }

    private boolean chatGrantsAccess(UUID caller, PluginData plugin, Branch branch, String chatId) {
        if (caller == null || chatId == null || chatId.isBlank()) {
            return false;
        }
        boolean installedOnBranch = installationClient.resolve(plugin.getId(), chatId)
                .filter(resolved -> branch.getId().equals(resolved.branchId()))
                .isPresent();
        return installedOnBranch && integrationClient.isMember(chatId, caller);
    }
}
