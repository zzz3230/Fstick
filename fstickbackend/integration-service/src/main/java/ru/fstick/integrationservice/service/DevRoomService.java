package ru.fstick.integrationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.fstick.integrationservice.dto.request.DevRoomNotifyRequest;
import ru.fstick.integrationservice.exception.ApiException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevRoomService {

    static final String EVENT_TYPE = "fstick.dev.notification";
    private static final Set<String> KINDS = Set.of("released", "rejected");

    private final IdentityService identityService;
    private final FstickProxyService proxyService;

    public void notifyAuthor(DevRoomNotifyRequest request) {
        if (request.getAuthorId() == null || request.getKind() == null || !KINDS.contains(request.getKind())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_notification",
                    "author_id is required and kind must be released or rejected");
        }

        Optional<String> mxid = identityService.lookup(request.getAuthorId());
        if (mxid.isEmpty()) {
            log.warn("Author {} has no known mxid, dropping {} notification", request.getAuthorId(), request.getKind());
            return;
        }

        try {
            proxyService.pushToMxid(mxid.get(), EVENT_TYPE, content(request));
        } catch (Exception ex) {
            log.warn("Failed to push {} to author {}: {}", EVENT_TYPE, request.getAuthorId(), ex.getMessage());
        }
    }

    private static Map<String, Object> content(DevRoomNotifyRequest request) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("author_id", request.getAuthorId().toString());
        content.put("kind", request.getKind());
        content.put("plugin_id", request.getPluginId() == null ? null : request.getPluginId().toString());
        content.put("plugin_name", request.getPluginName());
        content.put("semver", request.getSemver());
        if (request.getReason() != null) {
            content.put("reason", request.getReason());
        }
        return content;
    }
}
