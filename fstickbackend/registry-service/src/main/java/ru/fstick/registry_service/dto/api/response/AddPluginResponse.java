package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileUploadData;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AddPluginResponse {
    private UUID pluginId;
    private UUID versionId;
    private List<FileUploadData> uploads;
    private FileUploadData iconUpload;
}