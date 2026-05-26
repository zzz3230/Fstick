package ru.fstick.installationservice.client;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.UUID;

@Component
public class IntegrationClient {

    private final RestClient restClient;

    public IntegrationClient(@Value("${integration.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isMember(String userId, String chatId) {
        return getMemberInfo(userId, chatId) != null
                && Boolean.TRUE.equals(getMemberInfo(userId, chatId).getMember());
    }

    public boolean isAdmin(String userId, String chatId) {
        ChatMemberResponse info = getMemberInfo(userId, chatId);
        return info != null && Boolean.TRUE.equals(info.getMember())
                && "ADMIN".equals(info.getRole());
    }

    private ChatMemberResponse getMemberInfo(String userId, String chatId) {
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

    public void notifyPluginInstalled(String userId, UUID pluginId,
                                      String chatId, UUID versionId, UUID installationId) {
        pushEvent(userId, pluginId, chatId, "PLUGIN_INSTALLED", Map.of(
                "versionId", versionId.toString(),
                "installationId", installationId.toString()
        ));
    }

    public void notifyPluginUninstalled(String userId, UUID pluginId,
                                        String chatId, UUID installationId) {
        pushEvent(userId, pluginId, chatId, "PLUGIN_UNINSTALLED", Map.of(
                "installationId", installationId.toString()
        ));
    }

    private void pushEvent(String userId, UUID pluginId, String chatId,
                           String eventName, Map<String, Object> eventData) {
        restClient.post()
                .uri("/api/v1/events/push")
                .body(new PushEventRequest(userId, pluginId, chatId, eventName, eventData))
                .retrieve()
                .toBodilessEntity();
    }

    record PushEventRequest(
            String userId,
            UUID pluginId,
            String chatId,
            String eventName,
            Map<String, Object> eventData
    ) {}

    @Getter
    @Setter
    static class ChatMemberResponse {
        private Boolean member;
        private String role;
    }
}