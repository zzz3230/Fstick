package ru.fstick.integrationservice.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.fstick.integrationservice.dto.request.PushEventRequest;
import ru.fstick.integrationservice.dto.request.SendMessageRequest;
import ru.fstick.integrationservice.dto.response.ChatMemberResponse;
import ru.fstick.integrationservice.dto.response.ChatMemberRole;

import java.util.HashMap;
import java.util.Map;

@Service
public class FstickProxyService {

    private final RestClient restClient;

    public FstickProxyService(@Value("${fstick.proxy.base-url:http://localhost:8008/fstick}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public ChatMemberResponse getChatMember(String chatId, String userId) {
        try {
            UpstreamChatMemberResponse upstream = restClient.get()
                    .uri("/api/v1/chats/" + chatId + "/members/" + userId)
                    .retrieve()
                    .body(UpstreamChatMemberResponse.class);

            if (upstream == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from fstick API");
            }

            ChatMemberResponse response = new ChatMemberResponse();
            response.setUserId(upstream.userId != null ? upstream.userId : userId);
            response.setMember(upstream.isMember);
            response.setRole(parseRole(upstream.role));
            return response;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call fstick API", ex);
        }
    }

    public void sendMessage(String chatId, SendMessageRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("plugin_sender_id", request.getPluginSenderId() == null ? null : request.getPluginSenderId().toString());
        body.put("message", request.getMessage());

        try {
            restClient.post()
                    .uri("/api/v1/chats/" + chatId + "/messages")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call fstick API", ex);
        }
    }

    public void pushEvent(PushEventRequest request) {
        Map<String, Object> content = new HashMap<>();
        if (request.getEventData() != null) {
            content.putAll(request.getEventData());
        }
        if (request.getPluginId() != null) {
            content.put("plugin_id", request.getPluginId().toString());
        }
        if (request.getChatId() != null) {
            content.put("chat_id", request.getChatId());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", request.getUserId());
        body.put("type", request.getEventName());
        body.put("content", content);

        try {
            restClient.post()
                    .uri("/api/v1/events/push")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call fstick API", ex);
        }
    }

    private ChatMemberRole parseRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return ChatMemberRole.REGULAR;
        }

        try {
            return ChatMemberRole.valueOf(rawRole.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ChatMemberRole.REGULAR;
        }
    }

    private static final class UpstreamChatMemberResponse {
        @JsonProperty("is_member")
        private boolean isMember;

        @JsonProperty("user_id")
        private String userId;

        @JsonProperty("role")
        private String role;
    }
}
