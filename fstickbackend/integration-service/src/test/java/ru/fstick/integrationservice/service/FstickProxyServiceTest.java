package ru.fstick.integrationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FstickProxyServiceTest {

    // GET-цепочка: restClient.get() → uriSpec → headersSpec → responseSpec
    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec<?> getUriSpec;
    private RestClient.RequestHeadersSpec<?> getHeadersSpec;
    private RestClient.ResponseSpec getResponseSpec;

    // POST-цепочка: restClient.post() → bodyUriSpec → bodySpec → responseSpec
    private RestClient.RequestBodyUriSpec postUriSpec;
    private RestClient.RequestBodySpec postBodySpec;
    private RestClient.ResponseSpec postResponseSpec;

    private IdentityService identityService;
    private FstickProxyService service;

    private static final String CHAT_ID = "!room:homeserver.org";
    private static final String USER_MXID = "@user:homeserver.org";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        restClient       = mock(RestClient.class);
        getUriSpec       = mock(RestClient.RequestHeadersUriSpec.class);
        getHeadersSpec   = mock(RestClient.RequestHeadersSpec.class);
        getResponseSpec  = mock(RestClient.ResponseSpec.class);
        postUriSpec      = mock(RestClient.RequestBodyUriSpec.class);
        postBodySpec     = mock(RestClient.RequestBodySpec.class);
        postResponseSpec = mock(RestClient.ResponseSpec.class);

        doReturn(getUriSpec).when(restClient).get();
        doReturn(getHeadersSpec).when(getUriSpec).uri(anyString());
        doReturn(getResponseSpec).when(getHeadersSpec).retrieve();

        doReturn(postUriSpec).when(restClient).post();
        doReturn(postBodySpec).when(postUriSpec).uri(anyString());
        doReturn(postBodySpec).when(postBodySpec).body(any(Map.class));
        doReturn(postResponseSpec).when(postBodySpec).retrieve();

        identityService = mock(IdentityService.class);
        when(identityService.lookup(USER_ID)).thenReturn(Optional.of(USER_MXID));

        service = new FstickProxyService(restClient, identityService);
    }

    // ── getChatMember ─────────────────────────────────────────────────────────

    @Test
    void getChatMember_member_adminRole_returnsResponse() {
        when(getResponseSpec.body(any(Class.class)))
                .thenReturn(buildUpstream(true, USER_MXID, "ADMIN"));

        ChatMemberResponse result = service.getChatMember(CHAT_ID, USER_ID);

        assertTrue(result.isMember());
        assertEquals(USER_ID, result.getUserId());
        assertEquals(ChatMemberRole.ADMIN, result.getRole());
    }

    @Test
    void getChatMember_notMember_nullRole_returnsRegular() {
        when(getResponseSpec.body(any(Class.class)))
                .thenReturn(buildUpstream(false, USER_MXID, null));

        ChatMemberResponse result = service.getChatMember(CHAT_ID, USER_ID);

        assertFalse(result.isMember());
        assertEquals(ChatMemberRole.REGULAR, result.getRole());
    }

    @Test
    void getChatMember_returnsRequestedUuid_notMxid() {
        when(getResponseSpec.body(any(Class.class)))
                .thenReturn(buildUpstream(true, USER_MXID, "REGULAR"));

        ChatMemberResponse result = service.getChatMember(CHAT_ID, USER_ID);

        // upstream отдаёт MXID, наружу возвращается uuid из запроса
        assertEquals(USER_ID, result.getUserId());
    }

    @Test
    void getChatMember_upstreamReturnsNull_throwsBadGateway() {
        when(getResponseSpec.body(any(Class.class))).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getChatMember(CHAT_ID, USER_ID));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void getChatMember_upstream404_rethrowsSameStatus() {
        RestClientResponseException cause = mock(RestClientResponseException.class);
        when(cause.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
        when(cause.getResponseBodyAsString()).thenReturn("not found");
        when(getResponseSpec.body(any(Class.class))).thenThrow(cause);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getChatMember(CHAT_ID, USER_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void getChatMember_networkError_throwsBadGateway() {
        when(getResponseSpec.body(any(Class.class)))
                .thenThrow(new RestClientException("connection refused"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getChatMember(CHAT_ID, USER_ID));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void getChatMember_unknownRole_defaultsToRegular() {
        when(getResponseSpec.body(any(Class.class)))
                .thenReturn(buildUpstream(true, USER_MXID, "SUPERUSER"));

        ChatMemberResponse result = service.getChatMember(CHAT_ID, USER_ID);

        assertEquals(ChatMemberRole.REGULAR, result.getRole());
    }

    @Test
    void getChatMember_blankRole_defaultsToRegular() {
        when(getResponseSpec.body(any(Class.class)))
                .thenReturn(buildUpstream(true, USER_MXID, "   "));

        ChatMemberResponse result = service.getChatMember(CHAT_ID, USER_ID);

        assertEquals(ChatMemberRole.REGULAR, result.getRole());
    }

    @Test
    void getChatMember_unknownUuid_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(identityService.lookup(unknown)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.getChatMember(CHAT_ID, unknown));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("unknown_user", ex.getCode());
        verifyNoInteractions(getResponseSpec);
    }

    // ── getChatMembers ────────────────────────────────────────────────────────

    @Test
    void getChatMembers_convertsMxidsToUuidsInOneBatch() {
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        when(getResponseSpec.body(any(Class.class))).thenReturn(buildMembers("@a:hs", "@b:hs"));
        when(identityService.resolveBatch(List.of("@a:hs", "@b:hs")))
                .thenReturn(Map.of("@a:hs", uuidA, "@b:hs", uuidB));

        ChatMembersResponse result = service.getChatMembers(CHAT_ID);

        assertEquals(CHAT_ID, result.getChatId());
        assertEquals(List.of(uuidA, uuidB),
                result.getMembers().stream().map(ChatMemberResponse::getUserId).toList());
        verify(identityService, times(1)).resolveBatch(anyCollection());
    }

    @Test
    void getChatMemberMxids_returnsOnlyJoinedMembersWithoutUuidResolution() {
        FstickProxyService.UpstreamChatMembersResponse upstream = buildMembers("@a:hs");
        upstream.members = List.of(upstream.members.get(0), buildUpstream(false, "@left:hs", "REGULAR"));
        when(getResponseSpec.body(any(Class.class))).thenReturn(upstream);

        assertEquals(List.of("@a:hs"), service.getChatMemberMxids(CHAT_ID));
        verifyNoInteractions(identityService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushToMxid_postsTypeAndContentForMxid() {
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        service.pushToMxid(USER_MXID, "fstick.plugin.reloaded", Map.of("chat_id", CHAT_ID));

        ArgumentCaptor<Map> body = ArgumentCaptor.forClass(Map.class);
        verify(postBodySpec).body(body.capture());
        assertEquals(USER_MXID, body.getValue().get("user_id"));
        assertEquals("fstick.plugin.reloaded", body.getValue().get("type"));
        assertEquals(Map.of("chat_id", CHAT_ID), body.getValue().get("content"));
    }

    // ── broadcastPluginState ──────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void broadcast_userScopedField_projectedPerMemberUuid_andPushedToMxid() {
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        when(getResponseSpec.body(any(Class.class))).thenReturn(buildMembers("@a:hs", "@b:hs"));
        when(identityService.resolveBatch(anyCollection()))
                .thenReturn(Map.of("@a:hs", uuidA, "@b:hs", uuidB));
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        BroadcastPluginStateRequest request = new BroadcastPluginStateRequest();
        request.setPluginId("plugin");
        request.setChatId(CHAT_ID);
        request.setUserScopedFields(new String[]{"votes"});
        request.setState(Map.of("votes", Map.of(uuidA.toString(), 1, uuidB.toString(), 2), "round", 3));

        service.broadcastPluginState(request);

        ArgumentCaptor<Map> bodies = ArgumentCaptor.forClass(Map.class);
        verify(postBodySpec, times(2)).body(bodies.capture());
        Map<String, Map<String, Object>> byUser = new HashMap<>();
        for (Map body : bodies.getAllValues()) {
            byUser.put((String) body.get("user_id"), (Map<String, Object>) ((Map) body.get("content")).get("state"));
        }
        assertEquals(1, byUser.get("@a:hs").get("votes"));
        assertEquals(2, byUser.get("@b:hs").get("votes"));
        assertEquals(3, byUser.get("@a:hs").get("round"));
    }

    @Test
    void broadcast_targetedUserUuid_pushedToMxid() {
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        BroadcastPluginStateRequest request = new BroadcastPluginStateRequest();
        request.setPluginId("plugin");
        request.setChatId(CHAT_ID);
        request.setUserId(USER_ID);
        request.setState(Map.of("round", 1));

        service.broadcastPluginState(request);

        ArgumentCaptor<Map> body = ArgumentCaptor.forClass(Map.class);
        verify(postBodySpec).body(body.capture());
        assertEquals(USER_MXID, body.getValue().get("user_id"));
    }

    @Test
    void pushEvent_sendsMxidToUpstream() {
        when(postResponseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setEventName("EVENT");

        service.pushEvent(request);

        ArgumentCaptor<Map> body = ArgumentCaptor.forClass(Map.class);
        verify(postBodySpec).body(body.capture());
        assertEquals(USER_MXID, body.getValue().get("user_id"));
    }

    @Test
    void pushEvent_unknownUuid_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(identityService.lookup(unknown)).thenReturn(Optional.empty());

        PushEventRequest request = new PushEventRequest();
        request.setUserId(unknown);
        request.setEventName("EVENT");

        ApiException ex = assertThrows(ApiException.class, () -> service.pushEvent(request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ── sendMessage ───────────────────────────────────────────────────────────

    @Test
    void sendMessage_success() {
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        SendMessageRequest request = new SendMessageRequest();
        request.setPluginSenderId(UUID.randomUUID());
        request.setMessage("Hello!");

        assertDoesNotThrow(() -> service.sendMessage(CHAT_ID, request));
        verify(postResponseSpec).toBodilessEntity();
    }

    @Test
    void sendMessage_nullPluginSenderId_doesNotCrash() {
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        SendMessageRequest request = new SendMessageRequest();
        request.setPluginSenderId(null);
        request.setMessage("Hi");

        assertDoesNotThrow(() -> service.sendMessage(CHAT_ID, request));
    }

    @Test
    void sendMessage_upstream403_rethrowsForbidden() {
        RestClientResponseException cause = mock(RestClientResponseException.class);
        when(cause.getStatusCode()).thenReturn(HttpStatus.FORBIDDEN);
        when(cause.getResponseBodyAsString()).thenReturn("forbidden");
        when(postResponseSpec.toBodilessEntity()).thenThrow(cause);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.sendMessage(CHAT_ID, new SendMessageRequest()));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void sendMessage_networkError_throwsBadGateway() {
        when(postResponseSpec.toBodilessEntity())
                .thenThrow(new RestClientException("timeout"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.sendMessage(CHAT_ID, new SendMessageRequest()));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    // ── pushEvent ─────────────────────────────────────────────────────────────

    @Test
    void pushEvent_allFieldsPresent_success() {
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setPluginId(UUID.randomUUID());
        request.setChatId(CHAT_ID);
        request.setEventName("PLUGIN_INSTALLED");
        request.setEventData(Map.of("installationId", "abc-123"));

        assertDoesNotThrow(() -> service.pushEvent(request));
        verify(postResponseSpec).toBodilessEntity();
    }

    @Test
    void pushEvent_nullEventData_doesNotCrash() {
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setEventName("CUSTOM");
        request.setEventData(null);

        assertDoesNotThrow(() -> service.pushEvent(request));
    }

    @Test
    void pushEvent_nullPluginIdAndChatId_doesNotCrash() {
        when(postResponseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.ok().build());

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setEventName("EVENT");
        request.setPluginId(null);
        request.setChatId(null);

        assertDoesNotThrow(() -> service.pushEvent(request));
    }

    @Test
    void pushEvent_networkError_throwsBadGateway() {
        when(postResponseSpec.toBodilessEntity())
                .thenThrow(new RestClientException("connection refused"));

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setEventName("TEST");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.pushEvent(request));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void pushEvent_upstream500_rethrowsSameStatus() {
        RestClientResponseException cause = mock(RestClientResponseException.class);
        when(cause.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(cause.getResponseBodyAsString()).thenReturn("server error");
        when(postResponseSpec.toBodilessEntity()).thenThrow(cause);

        PushEventRequest request = new PushEventRequest();
        request.setUserId(USER_ID);
        request.setEventName("TEST");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.pushEvent(request));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private FstickProxyService.UpstreamChatMemberResponse buildUpstream(
            boolean isMember, String userId, String role) {
        FstickProxyService.UpstreamChatMemberResponse obj =
                new FstickProxyService.UpstreamChatMemberResponse();
        obj.isMember = isMember;
        obj.userId   = userId;
        obj.role     = role;
        return obj;
    }

    private FstickProxyService.UpstreamChatMembersResponse buildMembers(String... mxids) {
        FstickProxyService.UpstreamChatMembersResponse obj = new FstickProxyService.UpstreamChatMembersResponse();
        obj.members = Arrays.stream(mxids)
                .map(mxid -> buildUpstream(true, mxid, "REGULAR"))
                .toList();
        return obj;
    }
}
