package ru.fstick.registry_service.dto.api.request.commit;


import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CommitScreenshotsRequest {
    @NotEmpty
    private List<String> keys;
}

