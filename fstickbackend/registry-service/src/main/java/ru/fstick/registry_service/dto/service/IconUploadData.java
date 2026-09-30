package ru.fstick.registry_service.dto.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IconUploadData {
    private String uploadUrl;
    private String key;
}
