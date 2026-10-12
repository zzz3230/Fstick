package ru.fstick.registry_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitAssetsRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.PluginViewExtend;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.service.CodeService;
import ru.fstick.registry_service.service.PluginsService;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class PluginsController {

    private final PluginsService pluginsService;
    private final CodeService codeService;


    //получить список плагинов
    @GetMapping
    public PluginsView<?> getPlugins(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                     @RequestParam(required = false, defaultValue = "false") boolean owned,
                                     @RequestParam(required = false, defaultValue = "0") int page,
                                     @RequestParam(required = false, defaultValue = "20") Integer limit,
                                     @RequestParam(required = false, defaultValue = "") String category,
                                     @RequestParam(required = false, defaultValue = "") String search,
                                     @RequestParam(required = false, defaultValue = "plugin_name") String sort,
                                     @RequestParam(required = false, defaultValue = "asc") String order) {

        if (owned) {
            if (userId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "missing_user", "Header X-User-Id is required for owned=true");
            }
            return pluginsService.getOwnedPlugins(userId, page, limit, category, search, sort, order);
        }
        return pluginsService.getPlugins(page, limit, category, search, sort, order);
    }

    //добавить новый плагин
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddPluginResponse addPlugin(@RequestHeader("X-User-Id") UUID userId,
                                       @RequestBody @Valid AddPluginRequest request) {
        return pluginsService.createPlugin(request, userId);
    }

    //получить плагин по id
    @GetMapping("/{pluginId}")
    public PluginViewExtend getPlugin(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                      @PathVariable UUID pluginId,
                                      @RequestParam(name = "chat_id", required = false) String chatId) {

        return pluginsService.getPlugin(pluginId, userId, chatId);
    }

    //обновить метаданные плагина
    @PutMapping("/{pluginId}")
    public PluginViewExtend updatePlugin(@RequestHeader("X-User-Id") UUID userId,
                                         @PathVariable UUID pluginId,
                                         @RequestBody @Valid PluginRequest requestData) {

        return pluginsService.updatePlugin(pluginId, userId, requestData);
    }


    //удалить плагин по id
    @DeleteMapping("/{pluginId}")
    public ChangeStatusResponse deletePlugin(@RequestHeader("X-User-Id") UUID userId,
                                             @PathVariable UUID pluginId) {

        return pluginsService.deletePlugin(pluginId, userId);
    }

    @PostMapping("/{pluginId}/assets/commit")
    public PluginViewExtend commitAssets(@RequestHeader("X-User-Id") UUID userId,
                                         @PathVariable UUID pluginId,
                                         @RequestBody @Valid CommitAssetsRequest request) {

        return pluginsService.commitAssets(pluginId, userId, request);
    }

    @PostMapping("/{pluginId}/screenshots")
    public UpdateScreenshotsResponse updateScreenshots(@RequestHeader("X-User-Id") UUID userId,
                                                       @PathVariable UUID pluginId,
                                                       @RequestBody @Valid UpdateScreenshotsRequest request) {

        return pluginsService.updateScreenshots(pluginId, userId, request);
    }

    @DeleteMapping("/{pluginId}/screenshots/{screenshotId}")
    public void deleteScreenshot(@RequestHeader("X-User-Id") UUID userId,
                                 @PathVariable UUID pluginId,
                                 @PathVariable UUID screenshotId) {

        pluginsService.deleteScreenshot(pluginId, userId, screenshotId);
    }

    @PutMapping("/{pluginId}/status")
    public ChangeStatusResponse changeStatus(@RequestHeader("X-User-Id") UUID userId,
                                             @PathVariable UUID pluginId,
                                             @RequestBody Status status) {

        return pluginsService.changeStatus(pluginId, userId, status);
    }

    @GetMapping("/{pluginId}/code/client")
    public ResponseEntity<CodeResponse> getPluginCodeClient(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                                            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
                                                            @PathVariable UUID pluginId,
                                                            @RequestParam(name = "branch_id") UUID branchId,
                                                            @RequestParam(name = "chat_id", required = false) String chatId) {

        Optional<CodeResponse> code = codeService.getClientCode(pluginId, branchId, userId, chatId, ifNoneMatch);
        if (code.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .eTag("\"" + code.get().getSha() + "\"")
                .body(code.get());
    }

    @GetMapping("/{pluginId}/code/server")
    public CodeResponse getPluginCodeServer(@RequestHeader(value = "X-User-Id", required = false) UUID userId,
                                            @PathVariable UUID pluginId,
                                            @RequestParam(name = "branch_id") UUID branchId) {

        return codeService.getServerCode(pluginId, branchId, userId);
    }

}
