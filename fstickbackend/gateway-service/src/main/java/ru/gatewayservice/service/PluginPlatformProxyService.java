package ru.gatewayservice.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import ru.gatewayservice.routing.Downstream;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@Service
public class PluginPlatformProxyService {

    private static final List<String> FORWARDED_REQUEST_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.IF_NONE_MATCH, "X-User-Id");
    private static final List<String> FORWARDED_RESPONSE_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.ETAG, HttpHeaders.CACHE_CONTROL);

    private final RestClient restClient;
    private final MxidEnricher mxidEnricher;
    private final String registryBaseUrl;
    private final String installationBaseUrl;
    private final String runtimeBaseUrl;

    @Autowired
    public PluginPlatformProxyService(
            @Value("${services.registry.base-url}") String registryBaseUrl,
            @Value("${services.installation.base-url}") String installationBaseUrl,
            @Value("${services.runtime.base-url}") String runtimeBaseUrl,
            RestClient restClient,
            MxidEnricher mxidEnricher
    ) {
        this.restClient = restClient;
        this.mxidEnricher = mxidEnricher;
        this.registryBaseUrl = registryBaseUrl;
        this.installationBaseUrl = installationBaseUrl;
        this.runtimeBaseUrl = runtimeBaseUrl;
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, Downstream downstream, String path, String rawQuery,
                                          byte[] body, HttpServletRequest in) {
        String url = baseUrl(downstream) + path + (rawQuery == null || rawQuery.isEmpty() ? "" : "?" + rawQuery);
        boolean enrich = downstream != Downstream.RUNTIME && !path.contains("/code/");

        var request = restClient.method(method)
                .uri(URI.create(url))
                .headers(headers -> {
                    for (String name : FORWARDED_REQUEST_HEADERS) {
                        String value = in.getHeader(name);
                        if (value != null && !value.isBlank()) {
                            headers.set(name, value);
                        }
                    }
                });
        RestClient.RequestHeadersSpec<?> spec = body != null && body.length > 0 ? request.body(body) : request;

        return spec.exchange((req, response) -> {
            HttpHeaders headers = new HttpHeaders();
            for (String name : FORWARDED_RESPONSE_HEADERS) {
                String value = response.getHeaders().getFirst(name);
                if (value != null) {
                    headers.set(name, value);
                }
            }
            byte[] bytes;
            try {
                bytes = StreamUtils.copyToByteArray(response.getBody());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read downstream response", e);
            }
            MediaType contentType = headers.getContentType();
            if (enrich && response.getStatusCode().is2xxSuccessful() && bytes.length > 0
                    && contentType != null && contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                bytes = mxidEnricher.enrich(bytes);
            }
            return ResponseEntity.status(response.getStatusCode()).headers(headers).body(bytes);
        });
    }

    private String baseUrl(Downstream downstream) {
        return switch (downstream) {
            case REGISTRY -> registryBaseUrl;
            case INSTALLATION -> installationBaseUrl;
            case RUNTIME -> runtimeBaseUrl;
        };
    }
}
