package ru.fstick.registry_service.controller;

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

@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final PluginsService pluginsService;

    public ApiController(PluginsService pluginsService) {
        this.pluginsService = pluginsService;
    }

    //получить список плагинов
    @GetMapping("/plugins")
    public ResponseEntity<PluginsView> getPlugins(@RequestParam(required = false, defaultValue = "0") int page,
                                                  @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                  @RequestParam(required = false, defaultValue = "") String category,
                                                  @RequestParam(required = false, defaultValue = "") String search,
                                                  @RequestParam(required = false, defaultValue = "name") String sort,
                                                  @RequestParam(required = false, defaultValue = "asc") String order) {

        PluginsView response = pluginsService.getPlugins(page, limit, category, search, sort, order);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    //добавить новый плагин
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PluginView> addPlugin(@RequestPart("data") PluginRequest requestData,
                                                @RequestPart("archive") MultipartFile archive,
                                                @RequestPart("icon") MultipartFile icon,
                                                @RequestPart("screenshots") List<MultipartFile> screenshots) {

        PluginView response = pluginsService.addPlugin(requestData, archive, icon, screenshots);

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

}
