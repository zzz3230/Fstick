package ru.fstick.registry_service.dto.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;

@Data
public class AddPluginVersionRequest {
    @NotEmpty
    @Valid
    private List<FileRequest> files;
    @NotBlank
    @Size(max = 50)
    private String version;
    @NotBlank
    @Size(max = 2000)
    private String changelog;
    @NotBlank
    @Size(max = 100)
    private String runtime;
}
