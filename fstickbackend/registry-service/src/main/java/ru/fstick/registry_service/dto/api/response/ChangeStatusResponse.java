package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ChangeStatusResponse {
    private UUID pluginId;
    private String newStatus;
    private String oldStatus;
}
