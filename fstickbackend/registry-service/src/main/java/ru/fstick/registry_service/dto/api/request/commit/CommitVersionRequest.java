package ru.fstick.registry_service.dto.api.request.commit;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class CommitVersionRequest {
    @NotBlank
    private String version;
    @NotBlank
    private String changelog;
    @NotEmpty
    private List<String> keys;
}
