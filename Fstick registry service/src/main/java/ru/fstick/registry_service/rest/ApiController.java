package ru.fstick.registry_service.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.api.PluginsResponse;
import ru.fstick.registry_service.services.PluginsService;

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

}
