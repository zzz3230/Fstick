package ru.fstick.registry_service.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.fstick.registry_service.dto.api.requests.PluginRequest;
import ru.fstick.registry_service.dto.api.responsies.PluginResponse;
import ru.fstick.registry_service.dto.api.responsies.PluginsResponse;
import ru.fstick.registry_service.services.PluginsService;

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
    public ResponseEntity<PluginsResponse> getPlugins(@RequestParam(required = false, defaultValue = "0") int page,
                                                      @RequestParam(required = false, defaultValue = "20") Integer limit,
                                                      @RequestParam(required = false, defaultValue = "") String category,
                                                      @RequestParam(required = false, defaultValue = "") String search,
                                                      @RequestParam(required = false, defaultValue = "name") String sort,
                                                      @RequestParam(required = false, defaultValue = "asc") String order) {

        PluginsResponse response = pluginsService.getPlugins(page, limit, category, search, sort, order);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PluginResponse> addPlugin(@RequestPart("data") PluginRequest requestData,
                                                    @RequestPart("archive") MultipartFile archive,
                                                    @RequestPart("icon") MultipartFile icon,
                                                    @RequestPart("screenshots") List<MultipartFile> screenshots) {

        PluginResponse response = pluginsService.addPlugin(requestData, archive, icon, screenshots);

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

}
