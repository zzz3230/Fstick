package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;
@Data
@Builder
public class ScreenshotView {
    private UUID screenshotId;
    private String screenshotUrl;
}
