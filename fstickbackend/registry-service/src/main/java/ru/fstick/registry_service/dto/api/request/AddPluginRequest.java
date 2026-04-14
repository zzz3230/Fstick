package ru.fstick.registry_service.dto.api.request;

import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;

@Data
public class AddPluginRequest {
    private String version;
    private List<FileRequest> files;
    private FileRequest icon;
}

