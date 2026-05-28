package ru.fstick.integrationservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.fstick.integrationservice.dto.request.BroadcastPluginStateRequest;
import ru.fstick.integrationservice.service.FstickProxyService;

/**
 * Эндпоинты для синхронизации стейта плагина между участниками чата.
 */
@RestController
@RequestMapping("/api/v1/plugin-state")
@RequiredArgsConstructor
public class PluginStateController {

    private final FstickProxyService proxyService;

    /**
     * POST /api/v1/plugin-state/broadcast
     *
     * Вызывается из runtime-service после commitState.
     * Находит всех участников чата и рассылает им обновление стейта
     * через SSE-брокер дендрайта.
     *
     * Body: { "plugin_id": "...", "chat_id": "...", "state": {...} }
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Void> broadcast(@RequestBody BroadcastPluginStateRequest request) {
        proxyService.broadcastPluginState(request);
        return ResponseEntity.ok().build();
    }
}

