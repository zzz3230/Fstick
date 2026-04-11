package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.FileUploadData;

import java.util.List;
import java.util.UUID;


@Data
@Builder
public class UpdateAssetsResponse {
    private UUID pluginId;
    private List<FileUploadData> uploads;
}
