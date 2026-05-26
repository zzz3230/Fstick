package ru.fstick.integrationservice.dto.response;

import lombok.Data;

@Data
public class ChatMemberResponse {
    private String userId;
    private boolean member;
    private ChatMemberRole role;
}
