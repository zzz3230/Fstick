package ru.fstick.integrationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.fstick.integrationservice.dto.request.DevRoomNotifyRequest;
import ru.fstick.integrationservice.exception.ApiException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DevRoomServiceTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID PLUGIN = UUID.randomUUID();

    private IdentityService identityService;
    private FstickProxyService proxyService;
    private DevRoomService service;

    @BeforeEach
    void setUp() {
        identityService = mock(IdentityService.class);
        proxyService = mock(FstickProxyService.class);
        service = new DevRoomService(identityService, proxyService);
    }

    private DevRoomNotifyRequest request(String kind, String reason) {
        DevRoomNotifyRequest request = new DevRoomNotifyRequest();
        request.setAuthorId(AUTHOR);
        request.setKind(kind);
        request.setPluginId(PLUGIN);
        request.setPluginName("Clock");
        request.setSemver("1.2.0");
        request.setReason(reason);
        return request;
    }

    @Test
    void released_pushesNotificationToAuthorMxidWithoutReason() {
        when(identityService.lookup(AUTHOR)).thenReturn(Optional.of("@a:hs"));

        service.notifyAuthor(request("released", null));

        verify(proxyService).pushToMxid("@a:hs", "fstick.dev.notification", Map.of(
                "author_id", AUTHOR.toString(),
                "kind", "released",
                "plugin_id", PLUGIN.toString(),
                "plugin_name", "Clock",
                "semver", "1.2.0"));
    }

    @Test
    void rejected_includesReason() {
        when(identityService.lookup(AUTHOR)).thenReturn(Optional.of("@a:hs"));

        service.notifyAuthor(request("rejected", "needs work"));

        verify(proxyService).pushToMxid(eq("@a:hs"), eq("fstick.dev.notification"),
                argThat(content -> "needs work".equals(content.get("reason"))));
    }

    @Test
    void unknownAuthor_isDroppedWithoutError() {
        when(identityService.lookup(AUTHOR)).thenReturn(Optional.empty());

        service.notifyAuthor(request("released", null));

        verifyNoInteractions(proxyService);
    }

    @Test
    void pushFailure_isSwallowed() {
        when(identityService.lookup(AUTHOR)).thenReturn(Optional.of("@a:hs"));
        doThrow(new RuntimeException("down")).when(proxyService).pushToMxid(anyString(), anyString(), any());

        service.notifyAuthor(request("released", null));
    }

    @Test
    void invalidKindOrMissingAuthor_is400() {
        ApiException badKind = assertThrows(ApiException.class, () -> service.notifyAuthor(request("cancelled", null)));
        DevRoomNotifyRequest noAuthor = request("released", null);
        noAuthor.setAuthorId(null);
        ApiException missing = assertThrows(ApiException.class, () -> service.notifyAuthor(noAuthor));

        assertEquals(400, badKind.getStatus().value());
        assertEquals(400, missing.getStatus().value());
        verifyNoInteractions(proxyService);
    }
}
