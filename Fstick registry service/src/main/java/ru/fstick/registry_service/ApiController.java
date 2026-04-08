package ru.fstick.registry_service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.fstick.registry_service.dto.PaginationData;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    @GetMapping("/plugins")
    public ResponseEntity<Map<String, Object>> getPlugins(@RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer limit,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String search,
                             @RequestParam(required = false, defaultValue = "name") String sort,
                             @RequestParam(required = false, defaultValue = "asc") String order) {

        Map<String, Object> response = new HashMap<>();
        response.put("items", new HashMap<String, Object>() {});

            PaginationData paginationData = PaginationData.
                    builder().
                    page(0).
                    limit(0).
                    total(0).
                    totalPages(0).
                    hasNext(false).
                    hasPrev(false).
                    build();

        response.put("pagination", paginationData);

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

}
