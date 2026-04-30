package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class PushEventRequest {
    private String userId;
    private UUID pluginId;
    private String chatId;
    private String eventName;
    private Map<String, Object> eventData;
}
