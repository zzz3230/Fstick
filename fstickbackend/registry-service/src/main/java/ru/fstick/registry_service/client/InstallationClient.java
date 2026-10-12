package ru.fstick.registry_service.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.fstick.registry_service.exception.ApiException;

import java.util.Optional;
import java.util.UUID;

@Component
public class InstallationClient {

    private final RestClient restClient;

    public InstallationClient(@Value("${services.installation.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Optional<Resolved> resolve(UUID pluginId, String chatId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uri -> uri.path("/internal/installations/resolve")
                            .queryParam("plugin_id", pluginId)
                            .queryParam("chat_id", chatId)
                            .build())
                    .retrieve()
                    .body(Resolved.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw unavailable(ex);
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    private static ApiException unavailable(Exception ex) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "installation_unavailable",
                "Installation service unavailable: " + ex.getMessage());
    }

    public record Resolved(
            @JsonProperty("branch_id") UUID branchId,
            @JsonProperty("branch_status") String branchStatus
    ) {}
}
