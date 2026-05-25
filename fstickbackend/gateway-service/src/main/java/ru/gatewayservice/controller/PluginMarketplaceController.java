package ru.gatewayservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import ru.gatewayservice.service.PluginPlatformProxyService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PluginMarketplaceController {
    private final PluginPlatformProxyService proxyService;

    @GetMapping("/plugins")
    public ResponseEntity<String> getPlugins(@RequestParam MultiValueMap<String, String> queryParams) {
        return proxyService.listPlugins(queryParams);
    }

    @GetMapping("/plugins/{pluginId}")
    public ResponseEntity<String> getPlugin(@PathVariable String pluginId) {
        return proxyService.getPlugin(pluginId);
    }

    @PostMapping("/plugins")
    public ResponseEntity<String> initPluginUpload(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody String requestBody) {
        return proxyService.initPluginUpload(requestBody, userId);
    }

    @PostMapping("/plugins/{pluginId}/commit")
    public ResponseEntity<String> commitPluginUpload(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String pluginId,
            @RequestBody String requestBody
    ) {
        return proxyService.commitPluginUpload(pluginId, requestBody, userId);
    }

    @PostMapping("/installations")
    public ResponseEntity<String> installPlugin(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestBody String requestBody
    ) {
        return proxyService.installPlugin(chatId, userId, requestBody);
    }

    @GetMapping("/installations")
    public ResponseEntity<String> getInstallations(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam("chat_id") String chatId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return proxyService.listInstalledPlugins(chatId, userId, page, limit);
    }

    @PostMapping("/installations/confirm")
    public ResponseEntity<String> confirmInstall(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody String requestBody
    ) {
        return proxyService.confirmInstall(requestBody, userId);
    }

    @DeleteMapping("/installations/{installationId}")
    public ResponseEntity<String> uninstallPlugin(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String installationId
    ) {
        return proxyService.uninstallPlugin(installationId, userId);
    }
}

