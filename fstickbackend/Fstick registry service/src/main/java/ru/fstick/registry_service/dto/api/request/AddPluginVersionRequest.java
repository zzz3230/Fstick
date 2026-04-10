package ru.fstick.registry_service.dto.api.request;

import lombok.Data;

@Data
public class AddPluginVersionRequest {
    private String version;
    private String changelog;

}
