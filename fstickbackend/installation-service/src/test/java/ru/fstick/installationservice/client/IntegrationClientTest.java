package ru.fstick.installationservice.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IntegrationClientTest {

    @Test
    void pushInstallationChanged_unreachableIntegration_doesNotThrow() {
        IntegrationClient client = new IntegrationClient("http://localhost:1");

        assertDoesNotThrow(() -> client.pushInstallationChanged(
                "!room:homeserver.org", UUID.randomUUID(), UUID.randomUUID(), null));
    }
}
