package ru.gatewayservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.gatewayservice.routing.Downstream;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PluginPlatformProxyServiceTest {

    private static final String REGISTRY = "http://registry:8082";
    private static final String INSTALLATION = "http://installation:8081";
    private static final String RUNTIME = "http://runtime:8084";
    private static final String USER_UUID = "9f3c1a2e-0000-4000-8000-000000000001";

    private MockRestServiceServer server;
    private MxidEnricher enricher;
    private PluginPlatformProxyService service;
    private MockHttpServletRequest in;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        enricher = mock(MxidEnricher.class);
        service = new PluginPlatformProxyService(REGISTRY, INSTALLATION, RUNTIME, builder.build(), enricher);
        in = new MockHttpServletRequest();
        in.addHeader("X-User-Id", USER_UUID);
        in.addHeader("Cookie", "secret=1");
        in.addHeader("Content-Encoding", "gzip");
    }

    @Test
    void forwardsSelectedHeadersAndQuery() {
        in.addHeader("Accept", "application/json");
        in.addHeader("If-None-Match", "\"abc\"");
        in.addHeader("Content-Type", "application/json");
        server.expect(requestTo(REGISTRY + "/api/v1/plugins?owned=true&page=0"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-User-Id", USER_UUID))
                .andExpect(header("Accept", "application/json"))
                .andExpect(header("If-None-Match", "\"abc\""))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(headerDoesNotExist("Cookie"))
                .andExpect(headerDoesNotExist("Content-Encoding"))
                .andExpect(content().bytes("{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.forward(HttpMethod.POST, Downstream.REGISTRY, "/api/v1/plugins", "owned=true&page=0",
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8), in);

        server.verify();
    }

    @Test
    void notModifiedKeepsEtagAndCacheControl() {
        server.expect(requestTo(REGISTRY + "/api/v1/plugins/p1/code/client"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED)
                        .header("ETag", "\"v1\"")
                        .header("Cache-Control", "private, max-age=0")
                        .header("X-Internal", "leak"));

        ResponseEntity<byte[]> response = service.forward(HttpMethod.GET, Downstream.REGISTRY,
                "/api/v1/plugins/p1/code/client", null, null, in);

        assertEquals(HttpStatus.NOT_MODIFIED, response.getStatusCode());
        assertEquals("\"v1\"", response.getHeaders().getETag());
        assertEquals("private, max-age=0", response.getHeaders().getCacheControl());
        assertNull(response.getHeaders().getFirst("X-Internal"));
        verifyNoInteractions(enricher);
    }

    @Test
    void errorStatusAndBodyPassThroughWithoutEnrichment() {
        server.expect(requestTo(INSTALLATION + "/api/v1/installations/x"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"forbidden\"}"));

        ResponseEntity<byte[]> response = service.forward(HttpMethod.DELETE, Downstream.INSTALLATION,
                "/api/v1/installations/x", null, null, in);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertArrayEquals("{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8), response.getBody());
        verifyNoInteractions(enricher);
    }

    @Test
    void registryJsonIsEnriched() {
        byte[] enriched = "{\"enriched\":true}".getBytes(StandardCharsets.UTF_8);
        when(enricher.enrich(any())).thenReturn(enriched);
        server.expect(requestTo(REGISTRY + "/api/v1/plugins"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        ResponseEntity<byte[]> response = service.forward(HttpMethod.GET, Downstream.REGISTRY,
                "/api/v1/plugins", null, null, in);

        assertArrayEquals(enriched, response.getBody());
    }

    @Test
    void codeAndRuntimeResponsesAreNotEnriched() {
        server.expect(requestTo(REGISTRY + "/api/v1/plugins/p1/code/server"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(RUNTIME + "/plugins/p1/state?chat_id=%21r%3Ax"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.forward(HttpMethod.GET, Downstream.REGISTRY, "/api/v1/plugins/p1/code/server", null, null, in);
        service.forward(HttpMethod.GET, Downstream.RUNTIME, "/plugins/p1/state", "chat_id=%21r%3Ax", null, in);

        verify(enricher, never()).enrich(any());
        server.verify();
    }

    @Test
    void nonJsonResponseIsNotEnriched() {
        server.expect(requestTo(REGISTRY + "/api/v1/plugins/p1"))
                .andRespond(withSuccess("plain", MediaType.TEXT_PLAIN));

        ResponseEntity<byte[]> response = service.forward(HttpMethod.GET, Downstream.REGISTRY,
                "/api/v1/plugins/p1", null, null, in);

        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        verifyNoInteractions(enricher);
    }
}
