package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PluginEventPushRequest {
    private String type;
    private List<String> chatIds;
    private Map<String, Object> content;
}
