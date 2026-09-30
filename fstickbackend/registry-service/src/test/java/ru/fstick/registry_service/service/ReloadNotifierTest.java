package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.fstick.registry_service.client.InstallationClient;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReloadNotifierTest {

    private static final UUID PLUGIN = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();

    @Mock private InstallationClient installationClient;
    @Mock private IntegrationClient integrationClient;

    private ReloadNotifier notifier;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notifier = new ReloadNotifier(installationClient, integrationClient, Runnable::run);
    }

    @Test
    void clientChanged_pushesReloadedEventToChatsOfBranch() {
        when(installationClient.chatsForBranch(BRANCH)).thenReturn(List.of("!a:x", "!b:x"));

        notifier.clientChanged(PLUGIN, BRANCH);

        verify(integrationClient).pushPluginEvent("fstick.plugin.reloaded", List.of("!a:x", "!b:x"), Map.of(
                "plugin_id", PLUGIN.toString(),
                "branch_id", BRANCH.toString(),
                "client_changed", true));
    }

    @Test
    void clientChanged_noChats_pushesNothing() {
        when(installationClient.chatsForBranch(BRANCH)).thenReturn(List.of());

        notifier.clientChanged(PLUGIN, BRANCH);

        verify(integrationClient, never()).pushPluginEvent(any(), any(), any());
    }

    @Test
    void clientChanged_installationDown_isSwallowed() {
        when(installationClient.chatsForBranch(BRANCH)).thenThrow(
                new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "installation_unavailable", "down"));

        assertDoesNotThrow(() -> notifier.clientChanged(PLUGIN, BRANCH));
        verify(integrationClient, never()).pushPluginEvent(any(), any(), any());
    }
}
