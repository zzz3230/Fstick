package ru.fstick.installationservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.installationservice.entity.Installation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InstallationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final InstallationRowMapper rowMapper = new InstallationRowMapper();

    public InstallationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Installation save(Installation installation) {
        String sql = """
                INSERT INTO installations
                    (plugin_id, version_id, chat_id, installed_by)
                VALUES (?, ?, ?, ?)
                RETURNING *
                """;

        return jdbcTemplate.queryForObject(sql, rowMapper,
                installation.getPluginId(),
                installation.getVersionId(),
                installation.getChatId(),
                installation.getInstalledBy());
    }

    public Optional<Installation> findById(UUID installationId) {
        String sql = "SELECT * FROM installations WHERE installation_id = ?";

        List<Installation> result = jdbcTemplate.query(sql, rowMapper, installationId);
        return result.stream().findFirst();
    }

    public List<Installation> findAllByChatId(String chatId, int limit, int offset) {
        String sql = """
                SELECT * FROM installations
                WHERE chat_id = ?
                ORDER BY installed_at DESC
                LIMIT ? OFFSET ?
                """;

        return jdbcTemplate.query(sql, rowMapper, chatId, limit, offset);
    }

    public int countByChatId(String chatId) {
        String sql = "SELECT COUNT(*) FROM installations WHERE chat_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, chatId);
        return count != null ? count : 0;
    }

    public boolean existsByPluginIdAndChatId(UUID pluginId, String chatId) {
        String sql = """
                SELECT COUNT(*) FROM installations
                WHERE plugin_id = ? AND chat_id = ?
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pluginId, chatId);
        return count != null && count > 0;
    }

    public void deleteById(UUID installationId) {
        String sql = "DELETE FROM installations WHERE installation_id = ?";
        jdbcTemplate.update(sql, installationId);
    }
}