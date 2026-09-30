package ru.gatewayservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.gatewayservice.identity.IdentityCache;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MxidEnricher {

    private static final Map<String, String> SIBLINGS = Map.of(
            "author_id", "author_mxid",
            "installed_by", "installed_by_mxid",
            "claimed_by", "claimed_by_mxid"
    );

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final IdentityCache identityCache;

    private record Target(ObjectNode owner, String sourceField, String mxidField) {
    }

    public byte[] enrich(byte[] json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            List<Target> targets = new ArrayList<>();
            collect(root, targets);
            if (targets.isEmpty()) {
                return json;
            }
            Set<UUID> uuids = new HashSet<>();
            for (Target target : targets) {
                UUID uuid = parse(target.owner().get(target.sourceField()));
                if (uuid != null) {
                    uuids.add(uuid);
                }
            }
            Map<UUID, String> mxids = uuids.isEmpty() ? Map.of() : identityCache.lookupBatch(uuids);
            for (Target target : targets) {
                UUID uuid = parse(target.owner().get(target.sourceField()));
                String mxid = uuid == null ? null : mxids.get(uuid);
                if (mxid == null) {
                    target.owner().putNull(target.mxidField());
                } else {
                    target.owner().put(target.mxidField(), mxid);
                }
            }
            return MAPPER.writeValueAsBytes(root);
        } catch (RuntimeException ex) {
            log.warn("Response enrichment skipped: {}", ex.getMessage());
            return json;
        }
    }

    private void collect(JsonNode node, List<Target> targets) {
        if (node.isArray()) {
            node.forEach(child -> collect(child, targets));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        SIBLINGS.forEach((field, sibling) -> {
            if (object.has(field)) {
                targets.add(new Target(object, field, sibling));
            }
        });
        JsonNode author = object.get("author");
        if (author != null && author.isObject() && author.has("id")) {
            targets.add(new Target((ObjectNode) author, "id", "mxid"));
        }
        List<JsonNode> children = new ArrayList<>();
        object.forEach(children::add);
        children.forEach(child -> collect(child, targets));
    }

    private UUID parse(JsonNode value) {
        if (value == null || !value.isString()) {
            return null;
        }
        try {
            return UUID.fromString(value.asString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
