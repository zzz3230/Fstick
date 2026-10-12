package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.IconUploadData;

import java.util.UUID;

@Data
@Builder
public class AddPluginResponse {
    private UUID pluginId;
    private UUID devBranchId;
    private IconUploadData iconUpload;
}
