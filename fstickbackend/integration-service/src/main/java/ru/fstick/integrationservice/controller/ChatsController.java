package ru.fstick.integrationservice.controller;

import org.springframework.web.bind.annotation.*;
import ru.fstick.integrationservice.dto.request.SendMessageRequest;
import ru.fstick.integrationservice.dto.response.ChatMemberResponse;
import ru.fstick.integrationservice.dto.response.ChatMembersResponse;
import ru.fstick.integrationservice.service.FstickProxyService;

@RestController
@RequestMapping("/api/v1/chats")
public class ChatsController {

    private final FstickProxyService proxyService;

    public ChatsController(FstickProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /** GET /api/v1/chats/{chatId}/members/{userId} — проверить одного участника */
    @GetMapping("/{chatId}/members/{userId}")
    public ChatMemberResponse userInChat(@PathVariable String chatId, @PathVariable String userId) {
        return proxyService.getChatMember(chatId, userId);
    }

    /** GET /api/v1/chats/{chatId}/members — список всех участников чата */
    @GetMapping("/{chatId}/members")
    public ChatMembersResponse chatMembers(@PathVariable String chatId) {
        return proxyService.getChatMembers(chatId);
    }

    @PostMapping("/{chatId}/messages")
    public void sendMessage(@PathVariable String chatId, @RequestBody SendMessageRequest request) {
        proxyService.sendMessage(chatId, request);
    }
}
