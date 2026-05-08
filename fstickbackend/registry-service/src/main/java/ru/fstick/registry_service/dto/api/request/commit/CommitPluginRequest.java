package ru.fstick.registry_service.dto.api.request.commit;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CommitPluginRequest {
    @NotNull
    private UUID versionId;
    @NotEmpty
    private List<String> keys;
}
