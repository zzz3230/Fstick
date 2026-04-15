package ru.fstick.integrationservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller("/api/v1/chats")
public class ChatsController {

    @GetMapping("/{chatId}/members/{userId}")
    public void userInChat(@PathVariable String chatId, @PathVariable String userId){

    }
}
