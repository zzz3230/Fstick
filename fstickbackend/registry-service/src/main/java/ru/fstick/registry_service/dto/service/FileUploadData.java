package ru.fstick.registry_service.dto.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileUploadData {
    private String fileName;
    private String key;
    private String uploadUrl;
}
