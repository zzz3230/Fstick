package ru.fstick.registry_service.dto.api;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileUploadData;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AddPluginResponse {

    private UUID pluginId;
    private List<FileUploadData> uploads;
}