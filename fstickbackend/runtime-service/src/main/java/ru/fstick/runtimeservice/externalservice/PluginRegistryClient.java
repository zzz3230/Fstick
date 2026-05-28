package ru.fstick.runtimeservice.externalservice;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;
import ru.fstick.runtimeservice.dto.PluginDto;

import java.util.UUID;

@FeignClient(name = "plugin-registry-service", url = "${registry.service.url:http://localhost:8082}")
public interface PluginRegistryClient {

    @GetMapping("/api/v1/plugins/{pluginId}")
    PluginDto getPlugin(@PathVariable UUID pluginId);

    @GetMapping("/api/v1/plugins/{pluginId}/code/server")
    CodeLinksResponse getPluginCodeServer(@PathVariable UUID pluginId,
                                          @RequestParam String version,
                                          @RequestParam String runtime);
}
