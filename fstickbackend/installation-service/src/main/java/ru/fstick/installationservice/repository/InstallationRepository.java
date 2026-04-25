package ru.fstick.installationservice.repository;

import ru.fstick.installationservice.entity.Installation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallationRepository {

    Installation save(Installation installation);

    Optional<Installation> findById(UUID installationId);

    List<Installation> findAllByChatId(String chatId, int limit, int offset);

    int countByChatId(String chatId);

    boolean existsByPluginIdAndChatId(UUID pluginId, String chatId);

    Installation update(Installation installation);

    void deleteById(UUID installationId);
}