package ru.fstick.installationservice.service;

import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.request.UpdateInstallationRequest;
import ru.fstick.installationservice.dto.response.InstallationResponse;
import ru.fstick.installationservice.dto.response.InstallationShortResponse;
import ru.fstick.installationservice.dto.response.PaginatedResponse;

import java.util.UUID;

public interface InstallationService {

    InstallationResponse install(InstallRequest request, String chatId, String userId);

    PaginatedResponse<InstallationShortResponse> getAllByChatId(String chatId, String userId, int page, int limit);

    InstallationResponse getById(UUID installationId, String userId);

    InstallationResponse updateVersion(UUID installationId, UpdateInstallationRequest request, String userId);

    void uninstall(UUID installationId, String userId);
}