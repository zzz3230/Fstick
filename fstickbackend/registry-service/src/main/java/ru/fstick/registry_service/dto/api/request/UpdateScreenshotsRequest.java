package ru.fstick.registry_service.dto.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;

@Data
public class UpdateScreenshotsRequest {
    @NotEmpty
    @Valid
    private List<FileRequest> files;
}

