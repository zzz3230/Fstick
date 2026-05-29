package ru.fstick.runtimeservice.externalservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Клиент для integration-service.
 * После commitState вызывает broadcast, чтобы integration-service
 * нашёл всех участников чата и разослал им обновление стейта через дендрайт.
 */
@Component
public class IntegrationServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static IntegrationServiceClient instance;

    public IntegrationServiceClient(
            @Value("${integration.service.url:http://localhost:8080}") String baseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        IntegrationServiceClient.instance = this;
    }

    /**
     * Рассылает обновление стейта через integration-service.
     * integration-service сам делает проекцию: поля из userScopedFields
     * доставляются только соответствующему пользователю, остальное — всем.
     *
     * @param pluginId        идентификатор плагина
     * @param chatId          идентификатор чата
     * @param state           полный стейт после выполнения команды
     * @param userScopedFields множество имён полей, объявленных как Types.user_scoped
     */
    public void broadcastPluginState(UUID pluginId, String chatId, Object state, Set<String> userScopedFields) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("plugin_id", pluginId.toString());
            body.put("chat_id", chatId);
            body.put("state", state != null ? state : Map.of());
            body.put("user_scoped_fields", userScopedFields != null ? userScopedFields : Set.of());

            restClient.post()
                    .uri("/api/v1/plugin-state/broadcast")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("[IntegrationServiceClient] broadcast failed: " + e.getMessage());
        }
    }

    public void sendMessage(UUID pluginId, String chatId, String message){
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("plugin_sender_id", pluginId.toString());
            body.put("message", message);

            restClient.post()
                    .uri("/api/v1/chats/" + chatId + "/messages")
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.err.println("[IntegrationServiceClient] send message failed: " + e.getMessage());
        }
    }
}

