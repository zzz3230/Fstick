package ru.fstick.registry_service.dto.api.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class PluginRequest {
    @NotBlank
    @Size(max = 50)
    private String name;
    @NotBlank
    @Size(max = 2000)
    private String description;
    @Size(max = 50)
    private String category;
    @Size(max = 20)
    private List<String> tags;
}
