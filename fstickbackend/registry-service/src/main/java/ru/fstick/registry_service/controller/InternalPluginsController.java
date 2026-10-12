package ru.fstick.registry_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.response.CodeResponse;
import ru.fstick.registry_service.dto.api.response.InternalPluginResponse;
import ru.fstick.registry_service.service.CodeService;

import java.util.UUID;

@RestController
@RequestMapping("/internal/plugins")
@RequiredArgsConstructor
public class InternalPluginsController {

    private final CodeService codeService;

    @GetMapping("/{pluginId}")
    public InternalPluginResponse getPlugin(@PathVariable UUID pluginId) {
        return codeService.getInternalPlugin(pluginId);
    }

    @GetMapping("/{pluginId}/code/server")
    public CodeResponse getServerCode(@PathVariable UUID pluginId,
                                      @RequestParam(name = "branch_id") UUID branchId) {
        return codeService.getInternalServerCode(pluginId, branchId);
    }
}
