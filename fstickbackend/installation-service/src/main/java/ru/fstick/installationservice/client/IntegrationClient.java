package ru.fstick.installationservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class IntegrationClient {

    private static final String INSTALLATION_CHANGED = "fstick.plugin.installation_changed";

    private final RestClient restClient;

    public IntegrationClient(@Value("${integration.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isMember(UUID userId, String chatId) {
        return getMemberInfo(userId, chatId) != null
                && Boolean.TRUE.equals(getMemberInfo(userId, chatId).getMember());
    }

    public boolean isAdmin(UUID userId, String chatId) {
        ChatMemberResponse info = getMemberInfo(userId, chatId);
        return info != null && Boolean.TRUE.equals(info.getMember())
                && "ADMIN".equals(info.getRole());
    }

    private ChatMemberResponse getMemberInfo(UUID userId, String chatId) {
        try {
            return restClient.get()
                    .uri("/api/v1/chats/{chatId}/members/{userId}", chatId, userId)
                    .retrieve()
                    .body(ChatMemberResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 403 || status == 400) return null;
            throw new RuntimeException("Integration Service unavailable: HTTP " + status, ex);
        } catch (Exception ex) {
            throw new RuntimeException("Integration Service unavailable: " + ex.getMessage(), ex);
        }
    }

    public void notifyPluginInstalled(UUID userId, UUID pluginId,
                                      String chatId, UUID branchId, UUID installationId) {
        pushEvent(userId, pluginId, chatId, "PLUGIN_INSTALLED", Map.of(
                "branch_id", branchId.toString(),
                "installation_id", installationId.toString()
        ));
    }

    public void notifyPluginUninstalled(UUID userId, UUID pluginId,
                                        String chatId, UUID installationId) {
        pushEvent(userId, pluginId, chatId, "PLUGIN_UNINSTALLED", Map.of(
                "installation_id", installationId.toString()
        ));
    }

    public void pushInstallationChanged(String chatId, UUID pluginId,
                                        UUID installationId, UUID branchId) {
        Map<String, Object> content = new HashMap<>();
        content.put("plugin_id", pluginId.toString());
        content.put("installation_id", installationId.toString());
        content.put("branch_id", branchId != null ? branchId.toString() : null);
        try {
            restClient.post()
                    .uri("/api/v1/plugin-events/push")
                    .body(new PluginEventRequest(INSTALLATION_CHANGED, List.of(chatId), content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Failed to push {} for chat {}: {}", INSTALLATION_CHANGED, chatId, ex.getMessage());
        }
    }

    private void pushEvent(UUID userId, UUID pluginId, String chatId,
                           String eventName, Map<String, Object> eventData) {
        restClient.post()
                .uri("/api/v1/events/push")
                .body(new PushEventRequest(userId, pluginId, chatId, eventName, eventData))
                .retrieve()
                .toBodilessEntity();
    }

    record PluginEventRequest(
            String type,
            @JsonProperty("chat_ids") List<String> chatIds,
            Map<String, Object> content
    ) {}

    record PushEventRequest(
            @JsonProperty("user_id") UUID userId,
            @JsonProperty("plugin_id") UUID pluginId,
            @JsonProperty("chat_id") String chatId,
            @JsonProperty("event_name") String eventName,
            @JsonProperty("event_data") Map<String, Object> eventData
    ) {}

    @Getter
    @Setter
    static class ChatMemberResponse {
        private Boolean member;
        private String role;
    }
}