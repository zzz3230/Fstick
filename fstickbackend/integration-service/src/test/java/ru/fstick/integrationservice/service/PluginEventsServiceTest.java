package ru.fstick.integrationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.fstick.integrationservice.dto.request.PluginEventPushRequest;
import ru.fstick.integrationservice.exception.ApiException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PluginEventsServiceTest {

    private FstickProxyService proxyService;
    private Executor executor;
    private PluginEventsService service;

    @BeforeEach
    void setUp() {
        proxyService = mock(FstickProxyService.class);
        executor = mock(Executor.class);
        service = new PluginEventsService(proxyService, executor);
    }

    private PluginEventPushRequest request(String type, List<String> chatIds) {
        PluginEventPushRequest request = new PluginEventPushRequest();
        request.setType(type);
        request.setChatIds(chatIds);
        request.setContent(Map.of("plugin_id", "p1"));
        return request;
    }

    private void runQueued() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        task.getValue().run();
    }

    @Test
    void push_wrongTypePrefix_throwsBadRequest() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.push(request("m.room.message", List.of("!a:hs"))));

        assertEquals(400, ex.getStatus().value());
        verifyNoInteractions(executor, proxyService);
    }

    @Test
    void push_nullType_throwsBadRequest() {
        assertThrows(ApiException.class, () -> service.push(request(null, List.of("!a:hs"))));
    }

    @Test
    void push_returnsBeforeFanOut() {
        service.push(request("fstick.plugin.reloaded", List.of("!a:hs")));

        verify(executor).execute(any(Runnable.class));
        verifyNoInteractions(proxyService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fanOut_twoChats_pushesToEveryMemberWithChatId() {
        when(proxyService.getChatMemberMxids("!a:hs")).thenReturn(List.of("@u1:hs", "@u2:hs"));
        when(proxyService.getChatMemberMxids("!b:hs")).thenReturn(List.of("@u3:hs"));

        service.push(request("fstick.plugin.reloaded", List.of("!a:hs", "!b:hs")));
        runQueued();

        ArgumentCaptor<Map<String, Object>> content = ArgumentCaptor.forClass(Map.class);
        verify(proxyService).pushToMxid(eq("@u1:hs"),
                eq("fstick.plugin.reloaded"), content.capture());
        assertEquals(Map.of("plugin_id", "p1", "chat_id", "!a:hs"), content.getValue());
        verify(proxyService, times(3)).pushToMxid(anyString(), anyString(), any());
        verify(proxyService).pushToMxid(eq("@u3:hs"), anyString(),
                eq(Map.of("plugin_id", "p1", "chat_id", "!b:hs")));
    }

    @Test
    void fanOut_failureOnOneMemberOrChat_doesNotStopOthers() {
        when(proxyService.getChatMemberMxids("!a:hs")).thenThrow(new RuntimeException("down"));
        when(proxyService.getChatMemberMxids("!b:hs")).thenReturn(List.of("@u1:hs", "@u2:hs"));
        doThrow(new RuntimeException("boom")).when(proxyService).pushToMxid(
                eq("@u1:hs"), anyString(), any());

        service.push(request("fstick.plugin.reloaded", List.of("!a:hs", "!b:hs")));
        runQueued();

        verify(proxyService).pushToMxid(eq("@u2:hs"), anyString(), any());
        verify(proxyService, never()).pushToMxid(eq("@u3:hs"), anyString(), any());
    }
}
