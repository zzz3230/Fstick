package ru.fstick.registry_service.dto.api.request;

import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;

@Data
public class AddPluginVersionRequest {
    private List<FileRequest> files;
    private String version;
}
