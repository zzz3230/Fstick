package ru.gatewayservice.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

public final class CommandContextInjector {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private CommandContextInjector() {
    }

    public static byte[] inject(byte[] body, String pluginId, String chatId) {
        ObjectNode object = MAPPER.createObjectNode();
        if (body != null && body.length > 0) {
            JsonNode parsed = MAPPER.readTree(body);
            if (parsed.isObject()) {
                object = (ObjectNode) parsed;
            }
        }
        object.put("plugin_id", pluginId);
        if (chatId != null) {
            object.put("chat_id", chatId);
        }
        return MAPPER.writeValueAsString(object).getBytes(StandardCharsets.UTF_8);
    }
}
