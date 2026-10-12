package ru.fstick.integrationservice.service;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import ru.fstick.integrationservice.dto.request.BroadcastPluginStateRequest;
import ru.fstick.integrationservice.dto.request.PushEventRequest;
import ru.fstick.integrationservice.dto.request.SendMessageRequest;
import ru.fstick.integrationservice.dto.response.ChatMemberResponse;
import ru.fstick.integrationservice.dto.response.ChatMemberRole;
import ru.fstick.integrationservice.dto.response.ChatMembersResponse;
import ru.fstick.integrationservice.exception.ApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FstickProxyService {

    private final RestClient restClient;

    private final IdentityService identityService;

    @Autowired
    public FstickProxyService(@Value("${fstick.proxy.base-url:http://localhost:8008/fstick}") String baseUrl,
                              IdentityService identityService) {
        this(RestClient.builder().baseUrl(baseUrl).build(), identityService);
    }

    FstickProxyService(RestClient restClient, IdentityService identityService) {
        this.restClient = restClient;
        this.identityService = identityService;
    }

    public void broadcastPluginState(BroadcastPluginStateRequest req) {
        Map<String, Object> stateContent = new HashMap<>();
        stateContent.put("plugin_id", req.getPluginId());
        stateContent.put("chat_id", req.getChatId());

        // Базовое состояние из запроса
        Object rawState = req.getState();
        Map<String, Object> baseState = (rawState instanceof Map)
                ? new HashMap<>((Map<String, Object>) rawState)
                : new HashMap<>();

        if (req.getUserId() != null) {
            // 1. Адресная отправка конкретному пользователю
            stateContent.put("state", prepareStateForUser(baseState, req.getUserId(), req.getUserScopedFields()));
            pushStateEvent(mxidOf(req.getUserId()), stateContent);
        } else {
            // 2. Бродкаст всем участникам чата
            List<UpstreamChatMemberResponse> members = fetchMembers(req.getChatId());
            Map<String, UUID> uuids = identityService.resolveBatch(
                    members.stream().map(m -> m.userId).toList());
            for (UpstreamChatMemberResponse member : members) {
                try {
                    // Для каждого пользователя собираем его персональный stateContent
                    Map<String, Object> userSpecificContent = new HashMap<>(stateContent);
                    userSpecificContent.put("state",
                            prepareStateForUser(baseState, uuids.get(member.userId), req.getUserScopedFields()));

                    pushStateEvent(member.userId, userSpecificContent);
                } catch (Exception ex) {
                    // Log and continue — don't abort the whole broadcast
                }
            }
        }
    }

    /**
     * Метод адаптирует переданный state под конкретного userId,
     * схлопывая поля из userScopedFields до приватных данных этого пользователя.
     */
    private Map<String, Object> prepareStateForUser(Map<String, Object> baseState, UUID userId, String[] userScopedFields) {
        if (baseState.isEmpty()) {
            return baseState;
        }

        // Создаем глубокую/поверхностную копию верхнего уровня, чтобы не портить исходный baseState
        Map<String, Object> filteredState = new HashMap<>(baseState);

        if (userScopedFields == null || userScopedFields.length == 0) {
            return filteredState;
        }

        for (String field : userScopedFields) {
            if (filteredState.containsKey(field)) {
                Object fieldContent = filteredState.get(field);

                if (fieldContent instanceof Map) {
                    Map<String, Object> usersMap = (Map<String, Object>) fieldContent;
                    // Достаем данные конкретного пользователя. Если их нет — можно вернуть null или пустой Map
                    Object userPrivateData = userId == null ? null : usersMap.get(userId.toString());

                    // Заменяем мапу со всеми юзерами на объект конкретного юзера
                    filteredState.put(field, userPrivateData);
                }
            }
        }

        return filteredState;
    }

    private void pushStateEvent(String mxid, Map<String, Object> content) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", mxid);
        body.put("type", "fstick.plugin.state");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to push plugin state event", ex);
        }
    }

    public List<String> getChatMemberMxids(String chatId) {
        return fetchMembers(chatId).stream()
                .filter(m -> m.isMember)
                .map(m -> m.userId)
                .toList();
    }

    public void pushToMxid(String mxid, String type, Map<String, Object> content) {
        Map<String, Object> body = new HashMap<>();
        body.put("user_id", mxid);
        body.put("type", type);
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

    public ChatMembersResponse getChatMembers(String chatId) {
        List<UpstreamChatMemberResponse> upstreamMembers = fetchMembers(chatId);
        Map<String, UUID> uuids = identityService.resolveBatch(
                upstreamMembers.stream().map(m -> m.userId).toList());

        List<ChatMemberResponse> members = new ArrayList<>();
        for (UpstreamChatMemberResponse m : upstreamMembers) {
            ChatMemberResponse r = new ChatMemberResponse();
            r.setUserId(uuids.get(m.userId));
            r.setMember(m.isMember);
            r.setRole(parseRole(m.role));
            members.add(r);
        }

        ChatMembersResponse response = new ChatMembersResponse();
        response.setChatId(chatId);
        response.setMembers(members);
        return response;
    }

    private List<UpstreamChatMemberResponse> fetchMembers(String chatId) {
        try {
            UpstreamChatMembersResponse upstream = restClient.get()
                    .uri("/api/v1/chats/" + chatId + "/members")
                    .retrieve()
                    .body(UpstreamChatMembersResponse.class);

            if (upstream == null || upstream.members == null) {
                return List.of();
            }
            return upstream.members;
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to call fstick API", ex);
        }
    }

    public ChatMemberResponse getChatMember(String chatId, UUID userId) {
        String mxid = mxidOf(userId);
        try {
            UpstreamChatMemberResponse upstream = restClient.get()
                    .uri("/api/v1/chats/" + chatId + "/members/" + mxid)
                    .retrieve()
                    .body(UpstreamChatMemberResponse.class);

            if (upstream == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from fstick API");
            }

            ChatMemberResponse response = new ChatMemberResponse();
            response.setUserId(userId);
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
        body.put("user_id", mxidOf(request.getUserId()));
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

    private String mxidOf(UUID userId) {
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_user_id", "user_id is required");
        }
        return identityService.lookup(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown_user", "Unknown user"));
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

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class UpstreamChatMemberResponse {
        @JsonProperty("is_member")
        boolean isMember;

        @JsonProperty("user_id")
        String userId;

        @JsonProperty("role")
        String role;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static final class UpstreamChatMembersResponse {
        @JsonProperty("chat_id")
        String chatId;

        @JsonProperty("members")
        List<UpstreamChatMemberResponse> members;
    }
}
