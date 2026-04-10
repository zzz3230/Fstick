package ru.fstick.registry_service.service;

import org.springframework.stereotype.Service;
import ru.fstick.registry_service.dto.api.AddPluginRequest;
import ru.fstick.registry_service.dto.api.AddPluginResponse;
import ru.fstick.registry_service.dto.service.FileUploadData;

import java.util.List;
import java.util.UUID;

@Service
public class S3Service {
    public String generateUrl(String key) {
        return "test";
    }
}
