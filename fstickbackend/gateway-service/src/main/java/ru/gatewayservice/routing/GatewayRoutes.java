package ru.gatewayservice.routing;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ru.gatewayservice.routing.Downstream.INSTALLATION;
import static ru.gatewayservice.routing.Downstream.REGISTRY;
import static ru.gatewayservice.routing.Downstream.RUNTIME;

@Component
public class GatewayRoutes {

    private record Route(HttpMethod method, String pattern, Downstream downstream, String targetPath,
                         boolean injectCommandContext) {
    }

    private static final List<Route> ROUTES = List.of(
            registry(HttpMethod.GET, "/api/v1/plugins"),
            registry(HttpMethod.POST, "/api/v1/plugins"),
            registry(HttpMethod.GET, "/api/v1/plugins/{pluginId}"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/assets/commit"),
            registry(HttpMethod.GET, "/api/v1/plugins/{pluginId}/code/client"),
            registry(HttpMethod.GET, "/api/v1/plugins/{pluginId}/code/server"),
            registry(HttpMethod.GET, "/api/v1/plugins/{pluginId}/branches/{branchId}/edit"),
            registry(HttpMethod.PUT, "/api/v1/plugins/{pluginId}/branches/{branchId}/code"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/branches/{branchId}/reload"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/publish"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/branches/{branchId}/cancel"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/branches/{branchId}/claim"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/branches/{branchId}/approve"),
            registry(HttpMethod.POST, "/api/v1/plugins/{pluginId}/branches/{branchId}/reject"),
            registry(HttpMethod.GET, "/api/v1/moderation/branches"),
            new Route(HttpMethod.POST, "/api/v1/plugins/{pluginId}/command", RUNTIME, "/command", true),
            runtime(HttpMethod.GET, "/api/v1/plugins/{pluginId}/state"),
            runtime(HttpMethod.PUT, "/api/v1/plugins/{pluginId}/state"),
            installation(HttpMethod.POST, "/api/v1/installations"),
            installation(HttpMethod.GET, "/api/v1/installations"),
            installation(HttpMethod.POST, "/api/v1/installations/confirm"),
            installation(HttpMethod.PATCH, "/api/v1/installations/{installationId}"),
            installation(HttpMethod.DELETE, "/api/v1/installations/{installationId}")
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    public Optional<RouteMatch> match(HttpMethod method, String path) {
        for (Route route : ROUTES) {
            if (!route.method.equals(method) || !matcher.match(route.pattern, path)) {
                continue;
            }
            Map<String, String> variables = matcher.extractUriTemplateVariables(route.pattern, path);
            if (variables.values().stream().anyMatch(GatewayRoutes::isUnsafeSegment)) {
                return Optional.empty();
            }
            String target = route.targetPath;
            for (Map.Entry<String, String> variable : variables.entrySet()) {
                target = target.replace("{" + variable.getKey() + "}", variable.getValue());
            }
            return Optional.of(new RouteMatch(route.downstream, target, variables, route.injectCommandContext));
        }
        return Optional.empty();
    }

    private static boolean isUnsafeSegment(String segment) {
        String lower = segment.toLowerCase();
        return segment.isEmpty() || lower.equals(".") || lower.equals("..") || lower.contains("%2e")
                || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00");
    }

    private static Route registry(HttpMethod method, String path) {
        return new Route(method, path, REGISTRY, path, false);
    }

    private static Route installation(HttpMethod method, String path) {
        return new Route(method, path, INSTALLATION, path, false);
    }

    private static Route runtime(HttpMethod method, String path) {
        return new Route(method, path, RUNTIME, path.replace("/api/v1", ""), false);
    }
}
