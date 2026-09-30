package ru.fstick.registry_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.editor.EditResponse;
import ru.fstick.registry_service.dto.api.editor.ReloadResponse;
import ru.fstick.registry_service.dto.api.editor.SaveCodeRequest;
import ru.fstick.registry_service.dto.api.editor.SaveCodeResponse;
import ru.fstick.registry_service.service.EditorService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plugins/{pluginId}/branches/{branchId}")
@RequiredArgsConstructor
public class EditorController {

    private final EditorService editorService;

    @GetMapping("/edit")
    public EditResponse edit(@RequestHeader("X-User-Id") UUID userId,
                             @PathVariable UUID pluginId,
                             @PathVariable UUID branchId,
                             @RequestParam("chat_id") String chatId) {
        return editorService.edit(pluginId, branchId, userId, chatId);
    }

    @PutMapping("/code")
    public SaveCodeResponse save(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID pluginId,
                                 @PathVariable UUID branchId,
                                 @RequestParam("chat_id") String chatId,
                                 @RequestBody SaveCodeRequest request) {
        return editorService.save(pluginId, branchId, userId, chatId, request);
    }

    @PostMapping("/reload")
    public ReloadResponse reload(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID pluginId,
                                 @PathVariable UUID branchId,
                                 @RequestParam("chat_id") String chatId) {
        return editorService.reload(pluginId, branchId, userId, chatId);
    }
}
