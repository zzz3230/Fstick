package ru.fstick.registry_service.controller;

import com.sun.source.util.Plugin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.fstick.registry_service.dto.api.request.PluginRequest;
import ru.fstick.registry_service.dto.api.view.PluginView;
import ru.fstick.registry_service.dto.api.view.PluginsView;
import ru.fstick.registry_service.service.PluginsService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final PluginsService pluginsService;

    public ApiController(PluginsService pluginsService) {
        this.pluginsService = pluginsService;
    }

    //получить список плагинов
    @GetMapping("/plugins")
    public PluginsView getPlugins(@RequestParam(required = false, defaultValue = "0") int page,
                                                  @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                  @RequestParam(required = false, defaultValue = "") String category,
                                                  @RequestParam(required = false, defaultValue = "") String search,
                                                  @RequestParam(required = false, defaultValue = "name") String sort,
                                                  @RequestParam(required = false, defaultValue = "asc") String order) {

        return pluginsService.getPlugins(page, limit, category, search, sort, order);
    }

    //добавить новый плагин
    @PostMapping(path = "/plugins", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PluginView addPlugin(@RequestPart(value = "data", required = true) @Valid PluginRequest requestData,
                                                @RequestPart(value = "archive", required = true) MultipartFile archive,
                                                @RequestPart(value = "icon", required = false) MultipartFile icon,
                                                @RequestPart(value = "screenshots", required = false) List<MultipartFile> screenshots) {

        return pluginsService.addPlugin(requestData, archive, icon, screenshots);

    }


    //получить плагин по id
    @GetMapping("/plugins/{pluginId}")
    public PluginView getPlugin(@PathVariable UUID pluginId) {

        return pluginsService.getPlugin(pluginId);
    }

    //обновить метаданные плагина
    @PutMapping("/plugins/{pluginId}")
    public PluginView updatePlugin(@PathVariable UUID pluginId, @RequestBody @Valid PluginRequest requestData) {

        return pluginsService.updatePlugin(pluginId, requestData);
    }

}
