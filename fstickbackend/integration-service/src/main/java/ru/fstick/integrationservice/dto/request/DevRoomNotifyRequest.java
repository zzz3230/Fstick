package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class DevRoomNotifyRequest {
    private UUID authorId;
    private String kind;
    private UUID pluginId;
    private String pluginName;
    private String semver;
    private String reason;
}
