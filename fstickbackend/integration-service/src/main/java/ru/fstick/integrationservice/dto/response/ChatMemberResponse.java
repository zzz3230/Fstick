package ru.fstick.integrationservice.dto.response;

import lombok.Data;

import java.util.UUID;

@Data
public class ChatMemberResponse {
    private UUID userId;
    private boolean member;
    private ChatMemberRole role;
}
