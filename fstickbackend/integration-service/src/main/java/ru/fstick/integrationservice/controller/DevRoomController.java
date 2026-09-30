package ru.fstick.integrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.integrationservice.dto.request.DevRoomNotifyRequest;
import ru.fstick.integrationservice.service.DevRoomService;

@RestController
@RequestMapping("/api/v1/dev-room")
@RequiredArgsConstructor
public class DevRoomController {

    private final DevRoomService devRoomService;

    @PostMapping("/notify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void notify(@RequestBody DevRoomNotifyRequest request) {
        devRoomService.notifyAuthor(request);
    }
}
