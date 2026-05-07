package ru.fstick.installationservice.service;

import org.springframework.stereotype.Service;
import ru.fstick.installationservice.dto.request.InstallRequest;
import ru.fstick.installationservice.dto.request.UpdateInstallationRequest;
import ru.fstick.installationservice.dto.response.InstallationResponse;
import ru.fstick.installationservice.dto.response.InstallationShortResponse;
import ru.fstick.installationservice.dto.response.PaginatedResponse;
import ru.fstick.installationservice.entity.Installation;
import ru.fstick.installationservice.exception.InstallationNotFoundException;
import ru.fstick.installationservice.exception.PluginAlreadyInstalledException;
import ru.fstick.installationservice.repository.InstallationRepository;

import java.util.List;
import java.util.UUID;

@Service
public class InstallationServiceImpl implements InstallationService {

    private final InstallationRepository repository;

    public InstallationServiceImpl(InstallationRepository repository) {
        this.repository = repository;
    }

    @Override
    public InstallationResponse install(InstallRequest request, String chatId, String userId) {
        // Проверяем что пользователь участник чата (заглушка)
        checkChatMembership(userId, chatId);

        if (repository.existsByPluginIdAndChatId(request.getPluginId(), chatId)) {
            throw new PluginAlreadyInstalledException(request.getPluginId().toString(), chatId);
        }

        Installation installation = new Installation();
        installation.setPluginId(request.getPluginId());
        installation.setVersionId(request.getVersionId());
        installation.setChatId(chatId);
        installation.setInstalledBy(userId);

        Installation saved = repository.save(installation);
        return toResponse(saved);
    }

    @Override
    public PaginatedResponse<InstallationShortResponse> getAllByChatId(String chatId, String userId,
                                                                       int page, int limit) {
        checkChatMembership(userId, chatId);

        int offset = (page - 1) * limit;
        List<Installation> installations = repository.findAllByChatId(chatId, limit, offset);
        int totalCount = repository.countByChatId(chatId);

        List<InstallationShortResponse> data = installations.stream()
                .map(this::toShortResponse)
                .toList();

        return new PaginatedResponse<>(data, page, limit, totalCount);
    }

    @Override
    public InstallationResponse getById(UUID installationId, String userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        return toResponse(installation);
    }

    @Override
    public InstallationResponse updateVersion(UUID installationId,
                                              UpdateInstallationRequest request, String userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        installation.setVersionId(request.getVersionId());
        Installation updated = repository.update(installation);
        return toResponse(updated);
    }

    @Override
    public void uninstall(UUID installationId, String userId) {
        Installation installation = repository.findById(installationId)
                .orElseThrow(() -> new InstallationNotFoundException(installationId));

        checkChatMembership(userId, installation.getChatId());

        repository.deleteById(installationId);
    }

    // Заглушка для Integration Service
    private void checkChatMembership(String userId, String chatId) {
        // TODO: вызов к Integration Service
    }

    private InstallationResponse toResponse(Installation i) {
        return new InstallationResponse(
                i.getInstallationId(), i.getPluginId(), i.getVersionId(),
                i.getChatId(), i.getInstalledBy(), i.getInstalledAt(), i.getUpdatedAt()
        );
    }

    private InstallationShortResponse toShortResponse(Installation i) {
        return new InstallationShortResponse(
                i.getInstallationId(), i.getPluginId(), i.getVersionId(),
                i.getInstalledBy(), i.getInstalledAt()
        );
    }
}