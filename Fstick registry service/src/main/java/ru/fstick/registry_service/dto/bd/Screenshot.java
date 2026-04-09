package ru.fstick.registry_service.dto.bd;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class Screenshot {
    private UUID screenshotId;
    private String s3ScreenshotKey;
}
