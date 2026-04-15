package ru.fstick.registry_service.dto.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;
import java.util.UUID;

@Data
public class AddPluginRequest {
    private String version;
    private List<FileRequest> files;
    private FileRequest icon;
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    @NotBlank
    private UUID authorId;
    private String category;
    private List<String> tags;
}

