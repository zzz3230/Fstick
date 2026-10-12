package ru.gatewayservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import ru.gatewayservice.routing.GatewayRoutes;
import ru.gatewayservice.routing.RouteMatch;
import ru.gatewayservice.service.CommandContextInjector;
import ru.gatewayservice.service.PluginPlatformProxyService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final GatewayRoutes routes;
    private final PluginPlatformProxyService proxyService;

    @RequestMapping("/api/v1/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        Optional<RouteMatch> match = routes.match(method, request.getRequestURI());
        if (match.isEmpty()) {
            return ResponseEntity.status(404)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"not_found\",\"message\":\"Unknown route\"}".getBytes(StandardCharsets.UTF_8));
        }
        RouteMatch route = match.get();
        byte[] body = request.getInputStream().readAllBytes();
        if (route.injectCommandContext()) {
            body = CommandContextInjector.inject(body, route.variables().get("pluginId"),
                    queryParam(request.getQueryString(), "chat_id"));
        }
        return proxyService.forward(method, route.downstream(), route.path(), request.getQueryString(), body, request);
    }

    private String queryParam(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        String value = UriComponentsBuilder.newInstance().query(rawQuery).build().getQueryParams().getFirst(name);
        return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
    }
}
