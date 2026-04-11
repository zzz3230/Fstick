package ru.fstick.registry_service.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.api.request.*;
import ru.fstick.registry_service.dto.api.request.commit.CommitAssetsRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitPluginRequest;
import ru.fstick.registry_service.dto.api.request.commit.CommitVersionRequest;
import ru.fstick.registry_service.dto.api.response.*;
import ru.fstick.registry_service.dto.api.view.PluginViewExtend;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.service.PluginsService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plugins")
public class PluginsController {

    private final PluginsService pluginsService;

    public PluginsController(PluginsService pluginsService) {
        this.pluginsService = pluginsService;
    }

    //получить список плагинов
    @GetMapping
    public PluginsView getPlugins(@RequestParam(required = false, defaultValue = "0") int page,
                                  @RequestParam(required = false, defaultValue = "20") Integer limit,
                                  @RequestParam(required = false, defaultValue = "") String category,
                                  @RequestParam(required = false, defaultValue = "") String search,
                                  @RequestParam(required = false, defaultValue = "name") String sort,
                                  @RequestParam(required = false, defaultValue = "asc") String order) {

        return pluginsService.getPlugins(page, limit, category, search, sort, order);
    }

    //добавить новый плагин
    @PostMapping
    public AddPluginResponse addPlugin(@RequestBody AddPluginRequest request) {

        return pluginsService.initPluginUpload(request);
    }


    @PostMapping("/{pluginId}/commit")
    public PluginViewExtend commitPlugin(
            @PathVariable UUID pluginId,
            @RequestBody CommitPluginRequest request) {

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
                                               @RequestBody AddPluginVersionRequest request) {

        return pluginsService.addPluginVersion(pluginId, request);

    }

    @PostMapping("/{pluginId}/versions/commit")
    public PluginViewExtend commitVersion(
            @PathVariable UUID pluginId,
            @RequestBody CommitVersionRequest request) {

        return pluginsService.commitVersion(pluginId, request);
    }

    @PostMapping("/{pluginId}/assets")
    public UpdateAssetsResponse updateAssets(@PathVariable UUID pluginId,
                                             @RequestBody UpdateAssetsRequest request) {

        return pluginsService.updateAssets(pluginId, request);
    }

    @PostMapping("/{pluginId}/assets/commit")
    public PluginViewExtend commitAssets(@PathVariable UUID pluginId,
                                         @RequestBody CommitAssetsRequest request) {

        return pluginsService.commitAssets(pluginId, request);
    }

    @DeleteMapping("/{pluginId}/assets/{assetId}")
    public void deleteAssets(@PathVariable UUID pluginId,
                             @PathVariable UUID assetId) {

        pluginsService.deleteAsset(pluginId, assetId);
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
