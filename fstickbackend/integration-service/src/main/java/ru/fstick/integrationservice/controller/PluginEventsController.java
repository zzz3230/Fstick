package ru.fstick.integrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.integrationservice.dto.request.PluginEventPushRequest;
import ru.fstick.integrationservice.service.PluginEventsService;

@RestController
@RequestMapping("/api/v1/plugin-events")
@RequiredArgsConstructor
public class PluginEventsController {

    private final PluginEventsService pluginEventsService;

    @PostMapping("/push")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void push(@RequestBody PluginEventPushRequest request) {
        pluginEventsService.push(request);
    }
}
