package ru.fstick.registry_service.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.fstick.registry_service.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class IntegrationClient {

    private final RestClient restClient;

    public IntegrationClient(@Value("${services.integration.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isMember(String chatId, UUID userId) {
        try {
            MemberResponse response = restClient.get()
                    .uri("/api/v1/chats/{chatId}/members/{userId}", chatId, userId)
                    .retrieve()
                    .body(MemberResponse.class);
            return response != null && response.member();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 403 || status == 400) {
                return false;
            }
            throw unavailable(ex);
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    public void pushPluginEvent(String type, List<String> chatIds, Map<String, Object> content) {
        try {
            restClient.post()
                    .uri("/api/v1/plugin-events/push")
                    .body(new PluginEventRequest(type, chatIds, content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw unavailable(ex);
        }
    }

    private static ApiException unavailable(Exception ex) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "integration_unavailable",
                "Integration service unavailable: " + ex.getMessage());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemberResponse(
            @JsonProperty("member") @JsonAlias("is_member") boolean member
    ) {}

    private record PluginEventRequest(
            String type,
            @JsonProperty("chat_ids") List<String> chatIds,
            Map<String, Object> content
    ) {}
}
