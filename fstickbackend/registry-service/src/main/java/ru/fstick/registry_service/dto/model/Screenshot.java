package ru.fstick.registry_service.dto.model;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class Screenshot {
    private UUID pluginId;
    private UUID screenshotId;
    private String s3ScreenshotKey;
}
