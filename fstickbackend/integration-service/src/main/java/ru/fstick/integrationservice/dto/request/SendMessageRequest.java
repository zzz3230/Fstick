package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {
    private UUID pluginSenderId;
    private String message;
}
