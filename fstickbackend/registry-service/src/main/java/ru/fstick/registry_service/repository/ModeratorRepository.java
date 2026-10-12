package ru.fstick.registry_service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ModeratorRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean exists(UUID internalUuid) {
        String sql = "SELECT EXISTS (SELECT 1 FROM moderators WHERE internal_uuid = ?);";

        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, internalUuid));
    }

    public void grant(UUID internalUuid, UUID grantedBy) {
        String sql =
                """
                    INSERT INTO moderators (internal_uuid, granted_by) VALUES (?, ?)
                    ON CONFLICT DO NOTHING;
                """;

        jdbcTemplate.update(sql, internalUuid, grantedBy);
    }

    public void revoke(UUID internalUuid) {
        jdbcTemplate.update("DELETE FROM moderators WHERE internal_uuid = ?;", internalUuid);
    }
}
