package ru.fstick.registry_service.dto.api.view;

import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;

import java.util.List;
import java.util.UUID;
@Data
@Builder
public class PluginViewExtend {
    private UUID id;
    private UUID authorId;
    private String name;
    private String description;
    private String category;
    private List<String> tags;
    private String status;
    private String iconUrl;
    private String createdAt;
    private String updatedAt;
    private List<VersionView> versions;
    private List<ScreenshotView> screenshots;
}