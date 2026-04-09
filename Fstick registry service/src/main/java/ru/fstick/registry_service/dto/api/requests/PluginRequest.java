package ru.fstick.registry_service.dto.api.requests;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class PluginRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    private String category;
    private List<String> tags;
    @NotBlank
    private String version;
    @NotBlank
    private String changelog;
}
