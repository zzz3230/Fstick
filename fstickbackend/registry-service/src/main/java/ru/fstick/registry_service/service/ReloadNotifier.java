package ru.fstick.registry_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ReloadNotifier {

    static final String RELOADED = "fstick.plugin.reloaded";

    private final InstallationClient installationClient;
    private final IntegrationClient integrationClient;
    private final Executor executor;

    @Autowired
    public ReloadNotifier(InstallationClient installationClient, IntegrationClient integrationClient) {
        this(installationClient, integrationClient, Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "reload-notifier");
            thread.setDaemon(true);
            return thread;
        }));
    }

    ReloadNotifier(InstallationClient installationClient, IntegrationClient integrationClient, Executor executor) {
        this.installationClient = installationClient;
        this.integrationClient = integrationClient;
        this.executor = executor;
    }

    public void clientChanged(UUID pluginId, UUID branchId) {
        executor.execute(() -> notifyChats(pluginId, branchId));
    }

    private void notifyChats(UUID pluginId, UUID branchId) {
        try {
            List<String> chatIds = installationClient.chatsForBranch(branchId);
            if (chatIds.isEmpty()) {
                return;
            }
            integrationClient.pushPluginEvent(RELOADED, chatIds, Map.of(
                    "plugin_id", pluginId.toString(),
                    "branch_id", branchId.toString(),
                    "client_changed", true));
        } catch (Exception ex) {
            log.warn("Failed to push {} for branch {}: {}", RELOADED, branchId, ex.getMessage());
        }
    }
}
