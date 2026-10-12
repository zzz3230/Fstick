package ru.gatewayservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.gatewayservice.routing.Downstream;
import ru.gatewayservice.routing.GatewayRoutes;
import ru.gatewayservice.service.PluginPlatformProxyService;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProxyControllerTest {

    private PluginPlatformProxyService proxyService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        proxyService = mock(PluginPlatformProxyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProxyController(new GatewayRoutes(), proxyService)).build();
    }

    @Test
    void unknownRouteIsNotFoundAndNotForwarded() throws Exception {
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/installations/resolve")).andExpect(status().isNotFound());

        verifyNoInteractions(proxyService);
    }

    @Test
    void forwardsRawQueryAndBody() throws Exception {
        when(proxyService.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok("ok".getBytes(StandardCharsets.UTF_8)));

        mvc.perform(post("/api/v1/installations").queryParam("chat_id", "!r:x").content("{\"a\":1}"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(proxyService).forward(eq(HttpMethod.POST), eq(Downstream.INSTALLATION), eq("/api/v1/installations"),
                eq("chat_id=!r:x"), body.capture(), any());
        assertEquals("{\"a\":1}", new String(body.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void commandGetsPluginAndChatInjected() throws Exception {
        when(proxyService.forward(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());

        mvc.perform(post("/api/v1/plugins/p1/command").queryParam("chat_id", "!r:x").content("{\"command\":\"go\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(proxyService).forward(eq(HttpMethod.POST), eq(Downstream.RUNTIME), eq("/command"),
                eq("chat_id=!r:x"), body.capture(), any());
        String json = new String(body.getValue(), StandardCharsets.UTF_8);
        assertEquals("{\"command\":\"go\",\"plugin_id\":\"p1\",\"chat_id\":\"!r:x\"}", json);
    }
}
