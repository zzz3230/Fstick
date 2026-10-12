package ru.gatewayservice.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.gatewayservice.identity.IdentityCache;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final IdentityCache identityCache;

    public record MeResponse(@JsonProperty("internal_uuid") UUID internalUuid, @JsonProperty("mxid") String mxid) {
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<?> me(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        UUID uuid = parse(userId);
        String mxid = uuid == null ? null : identityCache.lookupBatch(List.of(uuid)).get(uuid);
        if (mxid == null) {
            return ResponseEntity.status(401)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"unauthorized\",\"message\":\"Unknown user\"}");
        }
        return ResponseEntity.ok(new MeResponse(uuid, mxid));
    }

    private UUID parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
