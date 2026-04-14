package ru.fstick.registry_service.dto.api.request.commit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CommitPluginRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    @NotBlank
    private UUID authorId;
    private String category;
    private List<String> tags;
    private List<String> keys;
}
