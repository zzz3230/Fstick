package ru.gatewayservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.util.UriComponentsBuilder;


import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class PluginPlatformProxyService {

    private final RestClient restClient;
    private final String registryBaseUrl;
    private final String installationBaseUrl;
    private final String runtimeBaseUrl;

    @Autowired
    public PluginPlatformProxyService(
            @Value("${services.registry.base-url}") String registryBaseUrl,
            @Value("${services.installation.base-url}") String installationBaseUrl,
            @Value("${services.runtime.base-url}") String runtimeBaseUrl
    ) {
        this.restClient = RestClient.create();
        this.registryBaseUrl = registryBaseUrl;
        this.installationBaseUrl = installationBaseUrl;
        this.runtimeBaseUrl = runtimeBaseUrl;
    }

    // Для тестов
    public PluginPlatformProxyService(
            @Value("${services.registry.base-url}") String registryBaseUrl,
            @Value("${services.installation.base-url}") String installationBaseUrl,
            RestClient restClient
    ) {
        this.restClient = restClient;
        this.registryBaseUrl = registryBaseUrl;
        this.installationBaseUrl = installationBaseUrl;
    }

    public ResponseEntity<String> listPlugins(MultiValueMap<String, String> queryParams) {
        String uri = UriComponentsBuilder
                .fromUriString(registryBaseUrl)
                .path("/api/v1/plugins")
                .queryParams(queryParams)
                .build(true)
                .toUriString();

        return forward(HttpMethod.GET, uri, null, null);
    }

    public ResponseEntity<String> getPlugin(String pluginId) {
        String uri = UriComponentsBuilder
                .fromUriString(registryBaseUrl)
                .path("/api/v1/plugins/{pluginId}")
                .buildAndExpand(pluginId)
                .toUriString();

        return forward(HttpMethod.GET, uri, null, null);
    }

    public ResponseEntity<String> initPluginUpload(String body, String userId) {
        String uri = UriComponentsBuilder
                .fromUriString(registryBaseUrl)
                .path("/api/v1/plugins")
                .build(true)
                .toUriString();

        return forward(HttpMethod.POST, uri, body, userId);
    }

    public ResponseEntity<String> commitPluginUpload(String pluginId, String body, String userId) {
        String uri = UriComponentsBuilder
                .fromUriString(registryBaseUrl)
                .path("/api/v1/plugins/{pluginId}/commit")
                .buildAndExpand(pluginId)
                .toUriString();

        return forward(HttpMethod.POST, uri, body, userId);
    }

    public ResponseEntity<String> getPluginState(String pluginId, String chatId, String userId) {
        String uri = UriComponentsBuilder
                .fromUriString(runtimeBaseUrl)
                .path("/plugins/{pluginId}/state")
                .queryParam("chat_id", chatId)
                .buildAndExpand(pluginId)
                .toUriString();
        return forward(HttpMethod.GET, uri, null, userId);
    }

    public ResponseEntity<String> executePluginCommand(String pluginId, String chatId, String userId, String body) {
        // Inject plugin_id and chat_id into request body (runtime-service uses SNAKE_CASE)
        String safePluginId = pluginId != null ? pluginId.replace("\"", "\\\"") : "";
        String injection = "\"plugin_id\":\"" + safePluginId + "\"";
        if (chatId != null) {
            injection += ",\"chat_id\":\"" + chatId.replace("\"", "\\\"") + "\"";
        }
        String enrichedBody;
        if (body != null && body.trim().startsWith("{")) {
            int pos = body.indexOf('{') + 1;
            enrichedBody = body.substring(0, pos) + injection + "," + body.substring(pos);
        } else {
            enrichedBody = "{" + injection + "}";
        }
        String uri = UriComponentsBuilder
                .fromUriString(runtimeBaseUrl)
                .path("/command")
                .build(true)
                .toUriString();
        return forward(HttpMethod.POST, uri, enrichedBody, userId);
    }

    public ResponseEntity<String> getPluginCodeClient(String pluginId, String version, String runtime) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(registryBaseUrl)
                .path("/api/v1/plugins/{pluginId}/code/client");
        if (version != null) builder.queryParam("version", version);
        if (runtime != null) builder.queryParam("runtime", runtime);
        String uri = builder.buildAndExpand(pluginId).toUriString();
        return forward(HttpMethod.GET, uri, null, null);
    }

    public ResponseEntity<String> installPlugin(String chatId, String userId, String body) {
        String uri = UriComponentsBuilder
                .fromUriString(installationBaseUrl)
                .path("/api/v1/installations")
                .queryParam("chat_id", chatId)
                .build(true)
                .toUriString();

        return forward(HttpMethod.POST, uri, body, userId);
    }

    public ResponseEntity<String> listInstalledPlugins(String chatId, String userId, Integer page, Integer limit) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(installationBaseUrl)
                .path("/api/v1/installations")
                .queryParam("chat_id", chatId);

        if (page != null) {
            builder.queryParam("page", page);
        }
        if (limit != null) {
            builder.queryParam("limit", limit);
        }

        return forward(HttpMethod.GET, builder.build(true).toUriString(), null, userId);
    }

    public ResponseEntity<String> confirmInstall(String body, String userId) {
        String uri = UriComponentsBuilder
                .fromUriString(installationBaseUrl)
                .path("/api/v1/installations/confirm")
                .build(true)
                .toUriString();

        return forward(HttpMethod.POST, uri, body, userId);
    }

    public ResponseEntity<String> uninstallPlugin(String installationId, String userId) {
        String uri = UriComponentsBuilder
                .fromUriString(installationBaseUrl)
                .path("/api/v1/installations/{installationId}")
                .buildAndExpand(installationId)
                .toUriString();

        return forward(HttpMethod.DELETE, uri, null, userId);
    }

    private ResponseEntity<String> forward(HttpMethod method, String uri, String body, String userId) {
        var requestBuilder = restClient
                .method(method)
                .uri(uri)
                .headers(headers -> {
                    if (userId != null && !userId.isBlank()) {
                        headers.set("X-User-Id", userId);
                    }
                    if (body != null) {
                        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
                    }
                });

        RequestHeadersSpec<?> requestSpec = body != null ? requestBuilder.body(body) : requestBuilder;

        return requestSpec.exchange((request, response) -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseHeaders.putAll(response.getHeaders());
                    responseHeaders.remove(HttpHeaders.TRANSFER_ENCODING);
                    responseHeaders.remove(HttpHeaders.CONTENT_LENGTH);

                    byte[] bytes;
                    try {
                        bytes = StreamUtils.copyToByteArray(response.getBody());
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read downstream response", e);
                    }
                    String responseBody = new String(bytes, StandardCharsets.UTF_8);
                    return ResponseEntity.status(response.getStatusCode()).headers(responseHeaders).body(responseBody);
                });
    }
}




