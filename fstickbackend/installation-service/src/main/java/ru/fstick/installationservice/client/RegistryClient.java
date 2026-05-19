package ru.fstick.installationservice.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

@Component
public class RegistryClient {

    private final RestClient restClient;

    public RegistryClient(@Value("${registry.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public PluginViewExtend getPlugin(UUID pluginId) {
        try {
            return restClient.get()
                    .uri("/api/v1/plugins/{pluginId}", pluginId)
                    .retrieve()
                    .body(PluginViewExtend.class);
        } catch (RestClientResponseException ex) {
            return null;
        }
    }

    @Getter @Setter
    public static class PluginViewExtend {
        private UUID id;
        private String status;
        private List<VersionView> versions;
    }

    @Getter @Setter
    public static class VersionView {
        private UUID versionId;
        private String version;
    }
}