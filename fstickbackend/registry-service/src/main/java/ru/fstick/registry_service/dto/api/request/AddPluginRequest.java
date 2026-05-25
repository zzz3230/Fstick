package ru.fstick.registry_service.dto.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;
import java.util.UUID;

@Data
public class AddPluginRequest {
    @NotBlank
    @Size(max = 50)
    private String version;
    @NotBlank
    @Size(max = 100)
    private String runtime;
    @NotEmpty
    @Valid
    private List<FileRequest> files;
    @NotNull
    @Valid
    private FileRequest icon;
    @NotBlank
    @Size(max = 50)
    private String name;
    @NotBlank
    @Size(max = 2000)
    private String description;
    private UUID authorId;
    @Size(max = 50)
    private String category;
    @Size(max = 20)
    private List<String> tags;
}

