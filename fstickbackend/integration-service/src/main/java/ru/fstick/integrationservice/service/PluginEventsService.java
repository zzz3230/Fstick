package ru.fstick.integrationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.fstick.integrationservice.dto.request.PluginEventPushRequest;
import ru.fstick.integrationservice.exception.ApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class PluginEventsService {

    private static final String TYPE_PREFIX = "fstick.plugin.";

    private final FstickProxyService proxyService;
    private final Executor executor;

    @Autowired
    public PluginEventsService(FstickProxyService proxyService) {
        this(proxyService, Executors.newFixedThreadPool(4));
    }

    PluginEventsService(FstickProxyService proxyService, Executor executor) {
        this.proxyService = proxyService;
        this.executor = executor;
    }

    public void push(PluginEventPushRequest request) {
        String type = request.getType();
        if (type == null || !type.startsWith(TYPE_PREFIX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_event_type",
                    "type must start with " + TYPE_PREFIX);
        }
        List<String> chatIds = request.getChatIds() == null ? List.of() : List.copyOf(request.getChatIds());
        Map<String, Object> content = request.getContent() == null ? Map.of() : Map.copyOf(request.getContent());
        executor.execute(() -> fanOut(type, chatIds, content));
    }

    private void fanOut(String type, List<String> chatIds, Map<String, Object> content) {
        for (String chatId : chatIds) {
            try {
                Map<String, Object> chatContent = new HashMap<>(content);
                chatContent.put("chat_id", chatId);
                for (String mxid : proxyService.getChatMemberMxids(chatId)) {
                    try {
                        proxyService.pushToMxid(mxid, type, chatContent);
                    } catch (Exception ex) {
                        log.warn("Failed to push {} to {} in {}: {}", type, mxid, chatId, ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to fan out {} in {}: {}", type, chatId, ex.getMessage());
            }
        }
    }
}
