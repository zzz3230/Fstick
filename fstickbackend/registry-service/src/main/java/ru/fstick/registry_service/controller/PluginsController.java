package ru.fstick.registry_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitScreenshotsRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitPluginRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitVersionRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.PluginViewExtend;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.service.PluginsService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class PluginsController {

    private final PluginsService pluginsService;


    //получить список плагинов
    @GetMapping
    public PluginsView getPlugins(@RequestParam(required = false, defaultValue = "0") int page,
                                  @RequestParam(required = false, defaultValue = "20") Integer limit,
                                  @RequestParam(required = false, defaultValue = "") String category,
                                  @RequestParam(required = false, defaultValue = "") String search,
                                  @RequestParam(required = false, defaultValue = "plugin_name") String sort,
                                  @RequestParam(required = false, defaultValue = "asc") String order) {

        return pluginsService.getPlugins(page, limit, category, search, sort, order);
    }

    //добавить новый плагин
    @PostMapping
    public AddPluginResponse addPlugin(@RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
                                       @RequestBody @Valid AddPluginRequest request) {

        UUID authorId = resolveAuthorId(userIdHeader, request.getAuthorId());

        if (authorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }

        return pluginsService.initPluginUpload(request, authorId);
    }

    /**
     * Resolves author UUID from either a raw UUID string or a Matrix user ID
     * (e.g. "@alice:localhost") by using a deterministic name-based UUID.
     * Falls back to the authorId from the request body if the header is absent.
     */
    private static UUID resolveAuthorId(String userIdHeader, UUID fallback) {
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                return UUID.fromString(userIdHeader);
            } catch (IllegalArgumentException e) {
                return UUID.nameUUIDFromBytes(userIdHeader.getBytes(StandardCharsets.UTF_8));
            }
        }
        return fallback;
    }

    //подтвердить создание плагина
    @PostMapping("/{pluginId}/commit")
    public PluginViewExtend commitPlugin(
            @PathVariable UUID pluginId,
            @RequestBody @Valid CommitPluginRequest request) {

        return pluginsService.commitPlugin(pluginId, request);
    }

    //получить плагин по id
    @GetMapping("/{pluginId}")
    public PluginViewExtend getPlugin(@PathVariable UUID pluginId) {

        return pluginsService.getPlugin(pluginId);
    }

    //обновить метаданные плагина
    @PutMapping("/{pluginId}")
    public PluginViewExtend updatePlugin(@PathVariable UUID pluginId,
                                         @RequestBody @Valid PluginRequest requestData) {

        return pluginsService.updatePlugin(pluginId, requestData);
    }


    //удалить плагин по id
    @DeleteMapping("/{pluginId}")
    public ChangeStatusResponse deletePlugin(@PathVariable UUID pluginId) {

        return pluginsService.deletePlugin(pluginId);
    }

    @PostMapping(path = "/{pluginId}/versions")
    public AddVersionResponse addPluginVersion(@PathVariable UUID pluginId,
                                               @RequestBody @Valid AddPluginVersionRequest request) {

        return pluginsService.initVersionUpload(pluginId, request);

    }

    @PostMapping("/{pluginId}/versions/commit")
    public PluginViewExtend commitVersion(
            @PathVariable UUID pluginId,
            @RequestBody @Valid CommitVersionRequest request) {

        return pluginsService.commitVersion(pluginId, request);
    }

    @PostMapping("/{pluginId}/screenshots")
    public UpdateScreenshotsResponse updateScreenshots(@PathVariable UUID pluginId,
                                                       @RequestBody @Valid UpdateScreenshotsRequest request) {

        return pluginsService.updateScreenshots(pluginId, request);
    }

    @PostMapping("/{pluginId}/screenshots/commit")
    public PluginViewExtend commitScreenshots(@PathVariable UUID pluginId,
                                              @RequestBody @Valid CommitScreenshotsRequest request) {

        return pluginsService.commitScreenshots(pluginId, request);
    }

    @DeleteMapping("/{pluginId}/screenshots/{screenshotId}")
    public void deleteScreenshot(@PathVariable UUID pluginId,
                                 @PathVariable UUID screenshotId) {

        pluginsService.deleteScreenshot(pluginId, screenshotId);
    }

    @PutMapping("/{pluginId}/status")
    public ChangeStatusResponse changeStatus(@PathVariable UUID pluginId,
                                             @RequestBody Status status) {

        return pluginsService.changeStatus(pluginId, status);
    }

    @GetMapping("/{pluginId}/code/client")
    public CodeLinksResponse getPluginCodeClient(@PathVariable UUID pluginId,
                                                 @RequestParam String version,
                                                 @RequestParam String runtime) {

        return pluginsService.getPluginCodeClient(pluginId, version, runtime);
    }

    @GetMapping("/{pluginId}/code/server")
    public CodeLinksResponse getPluginCodeServer(@PathVariable UUID pluginId,
                                                 @RequestParam String version,
                                                 @RequestParam String runtime) {

        return pluginsService.getPluginCodeServer(pluginId, version, runtime);
    }

}
