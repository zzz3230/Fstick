package ru.fstick.integrationservice.dto.request;

import lombok.Data;

@Data
public class BroadcastPluginStateRequest {
    private String pluginId;
    private String chatId;
    /** null → shared (broadcast to all members), non-null → push only to this user */
    private String userId;
    private Object state;
}
