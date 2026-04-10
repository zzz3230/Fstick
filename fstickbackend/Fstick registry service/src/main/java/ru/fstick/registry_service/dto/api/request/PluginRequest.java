package ru.fstick.registry_service.dto.api.request;

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
}
