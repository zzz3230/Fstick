package ru.fstick.registry_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.fstick.registry_service.exception.ApiException;

import java.time.Duration;
import java.util.UUID;

@Component
public class RuntimeClient {

    private final RestClient restClient;

    public RuntimeClient(@Value("${services.runtime.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public ReloadResult reload(UUID pluginId, String chatId, UUID branchId) {
        try {
            restClient.post()
                    .uri("/internal/plugins/{id}/reload", pluginId)
                    .body(new ReloadRequest(chatId, branchId))
                    .retrieve()
                    .toBodilessEntity();
            return new ReloadResult(true, null);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) {
                Throttled body = ex.getResponseBodyAs(Throttled.class);
                return new ReloadResult(false, body == null || body.retryAfterMs() == null ? 0 : body.retryAfterMs());
            }
            throw unavailable(ex);
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    private static ApiException unavailable(Exception ex) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "runtime_unavailable",
                "Runtime service unavailable: " + ex.getMessage());
    }

    public record ReloadResult(boolean reloaded, Long retryAfterMs) {}

    private record ReloadRequest(
            @JsonProperty("chat_id") String chatId,
            @JsonProperty("branch_id") UUID branchId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Throttled(@JsonProperty("retry_after_ms") Long retryAfterMs) {}
}
