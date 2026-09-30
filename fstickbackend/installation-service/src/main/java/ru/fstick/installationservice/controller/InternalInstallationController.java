package ru.fstick.installationservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.installationservice.dto.response.BranchChatsResponse;
import ru.fstick.installationservice.dto.response.ResolveResponse;
import ru.fstick.installationservice.service.InstallationService;

import java.util.UUID;

@RestController
@RequestMapping("/internal/installations")
public class InternalInstallationController {

    private final InstallationService service;

    public InternalInstallationController(InstallationService service) {
        this.service = service;
    }

    @GetMapping("/resolve")
    public ResolveResponse resolve(
            @RequestParam("plugin_id") UUID pluginId,
            @RequestParam("chat_id") String chatId
    ) {
        return service.resolve(pluginId, chatId);
    }

    @GetMapping
    public BranchChatsResponse chatsForBranch(@RequestParam("branch_id") UUID branchId) {
        return service.chatsForBranch(branchId);
    }
}
