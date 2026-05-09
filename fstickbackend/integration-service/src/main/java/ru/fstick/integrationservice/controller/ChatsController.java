package ru.fstick.integrationservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.fstick.integrationservice.dto.request.SendMessageRequest;
import ru.fstick.integrationservice.dto.response.ChatMemberResponse;
import ru.fstick.integrationservice.dto.response.ChatMemberRole;

@RestController()
@RequestMapping("/api/v1/chats")
public class ChatsController {

    @GetMapping("/{chatId}/members/{userId}")
    public ChatMemberResponse userInChat(@PathVariable String chatId, @PathVariable String userId){
        return new ChatMemberResponse();
    }

    @PostMapping("/{chatId}/messages")
    public void sendMessage(@PathVariable String chatId, @RequestBody SendMessageRequest request){

    }
}
