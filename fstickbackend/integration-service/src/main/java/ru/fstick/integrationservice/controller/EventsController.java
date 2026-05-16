package ru.fstick.integrationservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.integrationservice.dto.request.PushEventRequest;
import ru.fstick.integrationservice.service.FstickProxyService;

@RestController
@RequestMapping("/api/v1/events")
public class EventsController {

    private final FstickProxyService proxyService;

    public EventsController(FstickProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/push")
    public void pushEvent(@RequestBody PushEventRequest request) {
        proxyService.pushEvent(request);
    }
}
