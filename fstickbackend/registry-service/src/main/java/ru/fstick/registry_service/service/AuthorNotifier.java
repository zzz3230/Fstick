package ru.fstick.registry_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.model.PluginData;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class AuthorNotifier {

    static final String RELEASED = "released";
    static final String REJECTED = "rejected";

    private final IntegrationClient integrationClient;
    private final Executor executor;

    @Autowired
    public AuthorNotifier(IntegrationClient integrationClient) {
        this(integrationClient, Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "author-notifier");
            thread.setDaemon(true);
            return thread;
        }));
    }

    AuthorNotifier(IntegrationClient integrationClient, Executor executor) {
        this.integrationClient = integrationClient;
        this.executor = executor;
    }

    public void released(PluginData plugin, String semver) {
        send(plugin, RELEASED, semver, null);
    }

    public void rejected(PluginData plugin, String semver, String reason) {
        send(plugin, REJECTED, semver, reason);
    }

    private void send(PluginData plugin, String kind, String semver, String reason) {
        executor.execute(() -> {
            try {
                integrationClient.notifyDevRoom(plugin.getAuthorId(), kind, plugin.getId(), plugin.getName(), semver, reason);
            } catch (Exception ex) {
                log.warn("Failed to notify author of plugin {} ({}): {}", plugin.getId(), kind, ex.getMessage());
            }
        });
    }
}
