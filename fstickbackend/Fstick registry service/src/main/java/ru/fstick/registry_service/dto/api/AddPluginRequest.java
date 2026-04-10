package ru.fstick.registry_service.dto.api;

import lombok.Data;
import ru.fstick.registry_service.dto.service.FileRequest;

import java.util.List;

@Data
public class AddPluginRequest {
    private List<FileRequest> files;
}

