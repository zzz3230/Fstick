package ru.fstick.runtimeservice.mock;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * MockServer configuration — only active when profile "mock" is set.
 * In production, the real registry-service is used via PluginRegistryClient.
 */
@Configuration
@Profile("mock")
public class MockServerConfig {
    // MockServer disabled in production — no-op
}