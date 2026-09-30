package ru.fstick.registry_service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.model.ModerationAction;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ModerationLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public void append(UUID pluginId, UUID branchId, ModerationAction action, UUID actorId, String reason) {
        String sql =
                """
                    INSERT INTO plugin_moderation_log (plugin_id, branch_id, action, actor_id, reason)
                    VALUES (?, ?, ?, ?, ?);
                """;

        jdbcTemplate.update(sql, pluginId, branchId, action.name(), actorId, reason);
    }
}
