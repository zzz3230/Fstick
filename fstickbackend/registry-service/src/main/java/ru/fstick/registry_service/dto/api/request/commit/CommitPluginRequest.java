package ru.fstick.registry_service.dto.api.request.commit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CommitPluginRequest {
    private List<String> keys;
}
