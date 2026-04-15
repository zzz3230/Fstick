package ru.fstick.runtimeservice.externalservice;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.fstick.runtimeservice.dto.CodeLinksResponse;

import java.util.UUID;

@FeignClient(name = "plugin-registry-service", url = "http://localhost:1031")
public interface PluginRegistryClient {

    @GetMapping("/api/v1/plugins/{pluginId}/code/server")
    public CodeLinksResponse getPluginCodeServer(@PathVariable UUID pluginId,
                                                 @RequestParam String version,
                                                 @RequestParam String runtime);
}
