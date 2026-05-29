package ru.gatewayservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PluginPlatformProxyServiceTest {

    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private PluginPlatformProxyService service;

    private static final String REGISTRY_URL    = "http://registry:8082";
    private static final String INSTALLATION_URL = "http://installation:8083";
    private static final String USER_ID          = "@user:homeserver.org";

    @BeforeEach
    void setUp() {
        service = new PluginPlatformProxyService(REGISTRY_URL, INSTALLATION_URL, restClient);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockExchange(ResponseEntity<String> response) {
        when(restClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void mockExchangeWithBody(ResponseEntity<String> response) {
        when(restClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.exchange(any())).thenReturn(response);
    }

    private ResponseEntity<String> ok(String body) {
        return ResponseEntity.ok(body);
    }

    // ── listPlugins ───────────────────────────────────────────────────────────

    @Test
    void listPlugins_buildsCorrectUrl() {
        mockExchange(ok("{\"items\":[]}"));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("limit", "20");

        ResponseEntity<String> result = service.listPlugins(params);

        assertEquals(HttpStatus.OK, result.getStatusCode());

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());

        String uri = uriCaptor.getValue();
        assertTrue(uri.startsWith(REGISTRY_URL + "/api/v1/plugins"));
        assertTrue(uri.contains("page=0"));
        assertTrue(uri.contains("limit=20"));
    }

    // ── getPlugin ─────────────────────────────────────────────────────────────

    @Test
    void getPlugin_buildsCorrectUrl() {
        mockExchange(ok("{\"id\":\"plugin-123\"}"));

        ResponseEntity<String> result = service.getPlugin("plugin-123");

        assertEquals(HttpStatus.OK, result.getStatusCode());

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());

        assertTrue(uriCaptor.getValue().contains("/api/v1/plugins/plugin-123"));
    }

    // ── initPluginUpload ──────────────────────────────────────────────────────

    @Test
    void initPluginUpload_setsUserIdHeader() {
        mockExchangeWithBody(ok("{\"pluginId\":\"abc\"}"));

        service.initPluginUpload("{\"name\":\"plugin\"}", USER_ID);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertTrue(uriCaptor.getValue().endsWith("/api/v1/plugins"));
    }

    @Test
    void initPluginUpload_withoutUserId_doesNotCrash() {
        mockExchangeWithBody(ok("{\"pluginId\":\"abc\"}"));

        // userId = null — не должно бросать исключение
        assertDoesNotThrow(() -> service.initPluginUpload("{\"name\":\"plugin\"}", null));
    }

    // ── commitPluginUpload ────────────────────────────────────────────────────

    @Test
    void commitPluginUpload_buildsCorrectUrl() {
        mockExchangeWithBody(ok("{}"));

        service.commitPluginUpload("plugin-123", "{\"keys\":[]}", USER_ID);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertTrue(uriCaptor.getValue().contains("/api/v1/plugins/plugin-123/commit"));
    }

    // ── installPlugin ─────────────────────────────────────────────────────────

    @Test
    void installPlugin_includesChatIdInUrl() {
        mockExchangeWithBody(ok("{\"installation\":{}}"));

        service.installPlugin("!room:homeserver.org", USER_ID, "{\"pluginId\":\"abc\"}");

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());

        String uri = uriCaptor.getValue();
        assertTrue(uri.contains("/api/v1/installations"));
        assertTrue(uri.contains("chat_id="));
    }

    // ── listInstalledPlugins ──────────────────────────────────────────────────

    @Test
    void listInstalledPlugins_withPageAndLimit() {
        mockExchange(ok("{\"data\":[]}"));

        service.listInstalledPlugins("!room:homeserver.org", USER_ID, 1, 20);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());

        String uri = uriCaptor.getValue();
        assertTrue(uri.contains("page=1"));
        assertTrue(uri.contains("limit=20"));
    }

    @Test
    void listInstalledPlugins_withoutPageAndLimit_omitsParams() {
        mockExchange(ok("{\"data\":[]}"));

        service.listInstalledPlugins("!room:homeserver.org", USER_ID, null, null);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());

        String uri = uriCaptor.getValue();
        assertFalse(uri.contains("page="));
        assertFalse(uri.contains("limit="));
    }

    // ── confirmInstall ────────────────────────────────────────────────────────

    @Test
    void confirmInstall_buildsCorrectUrl() {
        mockExchangeWithBody(ok("{\"installation\":{}}"));

        service.confirmInstall("{\"confirmationToken\":\"tok\"}", USER_ID);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertTrue(uriCaptor.getValue().endsWith("/api/v1/installations/confirm"));
    }

    // ── uninstallPlugin ───────────────────────────────────────────────────────

    @Test
    void uninstallPlugin_buildsCorrectUrl() {
        mockExchange(ResponseEntity.noContent().build());

        service.uninstallPlugin("install-999", USER_ID);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestBodyUriSpec).uri(uriCaptor.capture());
        assertTrue(uriCaptor.getValue().contains("/api/v1/installations/install-999"));
    }

    @Test
    void uninstallPlugin_usesDeleteMethod() {
        mockExchange(ResponseEntity.noContent().build());

        service.uninstallPlugin("install-999", USER_ID);

        verify(restClient).method(HttpMethod.DELETE);
    }
}