package ru.fstick.integrationservice.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class ChatMembersResponse {
    private String chatId;
    private List<ChatMemberResponse> members;
}

