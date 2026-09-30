package ru.fstick.registry_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.fstick.registry_service.client.IntegrationClient;
import ru.fstick.registry_service.dto.model.PluginData;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static ru.fstick.registry_service.TestData.plugin;

@ExtendWith(MockitoExtension.class)
class AuthorNotifierTest {

    private static final UUID PLUGIN_ID = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();

    @Mock private IntegrationClient integrationClient;

    private AuthorNotifier notifier;
    private PluginData plugin;

    @BeforeEach
    void setUp() {
        notifier = new AuthorNotifier(integrationClient, Runnable::run);
        plugin = plugin(PLUGIN_ID, AUTHOR);
    }

    @Test
    void released_sendsKindReleasedWithoutReason() {
        notifier.released(plugin, "1.2.0");

        verify(integrationClient).notifyDevRoom(AUTHOR, "released", PLUGIN_ID, "Test Plugin", "1.2.0", null);
    }

    @Test
    void rejected_sendsKindRejectedWithReason() {
        notifier.rejected(plugin, "1.2.0", "needs work");

        verify(integrationClient).notifyDevRoom(AUTHOR, "rejected", PLUGIN_ID, "Test Plugin", "1.2.0", "needs work");
    }

    @Test
    void integrationFailure_isSwallowed() {
        doThrow(new RuntimeException("down")).when(integrationClient)
                .notifyDevRoom(AUTHOR, "released", PLUGIN_ID, "Test Plugin", "1.2.0", null);

        notifier.released(plugin, "1.2.0");
    }
}
