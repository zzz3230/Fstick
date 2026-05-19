package ru.fstick.installationservice.client;

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
        try {
            ChatMemberResponse response = restClient.get()
                    .uri("/api/v1/chats/{chatId}/members/{userId}", chatId, userId)
                    .retrieve()
                    .body(ChatMemberResponse.class);
            return response != null && Boolean.TRUE.equals(response.getMember());
        } catch (RestClientResponseException ex) {
            return false;
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

    static class ChatMemberResponse {
        private Boolean member;
        public Boolean getMember() { return member; }
        public void setMember(Boolean member) { this.member = member; }
    }
}