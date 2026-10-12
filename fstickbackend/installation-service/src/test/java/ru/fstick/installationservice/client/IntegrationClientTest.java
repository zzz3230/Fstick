package ru.fstick.installationservice.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IntegrationClientTest {

    private final IntegrationClient client = new IntegrationClient("http://localhost:1");

    @Test
    void pushInstallationChanged_unreachableIntegration_doesNotThrow() {
        assertDoesNotThrow(() -> client.pushInstallationChanged(
                "!room:homeserver.org", UUID.randomUUID(), UUID.randomUUID(), null));
    }

    @Test
    void notifyPluginInstalled_unreachableIntegration_doesNotThrow() {
        assertDoesNotThrow(() -> client.notifyPluginInstalled(
                UUID.randomUUID(), UUID.randomUUID(), "!room:homeserver.org", UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void notifyPluginUninstalled_unreachableIntegration_doesNotThrow() {
        assertDoesNotThrow(() -> client.notifyPluginUninstalled(
                UUID.randomUUID(), UUID.randomUUID(), "!room:homeserver.org", UUID.randomUUID()));
    }
}
