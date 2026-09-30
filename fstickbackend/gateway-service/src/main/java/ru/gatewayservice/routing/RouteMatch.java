package ru.gatewayservice.routing;

import java.util.Map;

public record RouteMatch(Downstream downstream, String path, Map<String, String> variables, boolean injectCommandContext) {
}
