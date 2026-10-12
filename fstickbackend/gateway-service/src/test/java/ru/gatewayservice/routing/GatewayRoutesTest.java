package ru.gatewayservice.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRoutesTest {

    private final GatewayRoutes routes = new GatewayRoutes();

    @ParameterizedTest
    @CsvSource({
            "GET,/api/v1/plugins,REGISTRY,/api/v1/plugins",
            "POST,/api/v1/plugins,REGISTRY,/api/v1/plugins",
            "GET,/api/v1/plugins/p1,REGISTRY,/api/v1/plugins/p1",
            "POST,/api/v1/plugins/p1/assets/commit,REGISTRY,/api/v1/plugins/p1/assets/commit",
            "GET,/api/v1/plugins/p1/code/client,REGISTRY,/api/v1/plugins/p1/code/client",
            "GET,/api/v1/plugins/p1/code/server,REGISTRY,/api/v1/plugins/p1/code/server",
            "GET,/api/v1/plugins/p1/branches/b1/edit,REGISTRY,/api/v1/plugins/p1/branches/b1/edit",
            "PUT,/api/v1/plugins/p1/branches/b1/code,REGISTRY,/api/v1/plugins/p1/branches/b1/code",
            "POST,/api/v1/plugins/p1/branches/b1/reload,REGISTRY,/api/v1/plugins/p1/branches/b1/reload",
            "POST,/api/v1/plugins/p1/publish,REGISTRY,/api/v1/plugins/p1/publish",
            "POST,/api/v1/plugins/p1/branches/b1/cancel,REGISTRY,/api/v1/plugins/p1/branches/b1/cancel",
            "POST,/api/v1/plugins/p1/branches/b1/claim,REGISTRY,/api/v1/plugins/p1/branches/b1/claim",
            "POST,/api/v1/plugins/p1/branches/b1/approve,REGISTRY,/api/v1/plugins/p1/branches/b1/approve",
            "POST,/api/v1/plugins/p1/branches/b1/reject,REGISTRY,/api/v1/plugins/p1/branches/b1/reject",
            "GET,/api/v1/moderation/branches,REGISTRY,/api/v1/moderation/branches",
            "POST,/api/v1/plugins/p1/command,RUNTIME,/command",
            "GET,/api/v1/plugins/p1/state,RUNTIME,/plugins/p1/state",
            "PUT,/api/v1/plugins/p1/state,RUNTIME,/plugins/p1/state",
            "POST,/api/v1/installations,INSTALLATION,/api/v1/installations",
            "GET,/api/v1/installations,INSTALLATION,/api/v1/installations",
            "POST,/api/v1/installations/confirm,INSTALLATION,/api/v1/installations/confirm",
            "PATCH,/api/v1/installations/i1,INSTALLATION,/api/v1/installations/i1",
            "DELETE,/api/v1/installations/i1,INSTALLATION,/api/v1/installations/i1"
    })
    void listedRoutesAreMapped(String method, String path, Downstream downstream, String target) {
        RouteMatch match = routes.match(HttpMethod.valueOf(method), path).orElseThrow();

        assertEquals(downstream, match.downstream());
        assertEquals(target, match.path());
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/v1/plugins/p1/commit",
            "DELETE,/api/v1/plugins/p1",
            "PUT,/api/v1/plugins/p1",
            "GET,/api/v1/plugins/p1/branches/b1/code",
            "POST,/api/v1/plugins/p1/state",
            "GET,/api/v1/internal/installations/resolve",
            "GET,/api/v1/plugins/p1/internal/x",
            "GET,/internal/anything",
            "GET,/api/v1/installations/i1",
            "GET,/api/v1/unknown",
            "GET,/api/v1/plugins/../state",
            "GET,/api/v1/plugins/%2e%2e",
            "GET,/api/v1/plugins/a%2Fb"
    })
    void unlistedRoutesAreRejected(String method, String path) {
        assertEquals(Optional.empty(), routes.match(HttpMethod.valueOf(method), path));
    }

    @Test
    void commandRouteCarriesPluginVariableAndInjectionFlag() {
        RouteMatch match = routes.match(HttpMethod.POST, "/api/v1/plugins/p1/command").orElseThrow();

        assertTrue(match.injectCommandContext());
        assertEquals("p1", match.variables().get("pluginId"));
    }
}
