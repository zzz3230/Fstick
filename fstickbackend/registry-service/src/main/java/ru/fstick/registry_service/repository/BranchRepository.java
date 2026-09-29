package ru.fstick.registry_service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.repository.mapper.BranchRowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BranchRepository {

    private static final String COLUMNS =
            """
                branch_id, plugin_id, status, semver, client_blob_sha, server_blob_sha,
                runtime_client, runtime_server, base_branch_id, created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final BranchRowMapper branchRowMapper;

    public UUID createDevBranch(UUID pluginId, String clientBlobSha, String serverBlobSha) {
        String sql =
                """
                    INSERT INTO branches (plugin_id, status, client_blob_sha, server_blob_sha)
                    VALUES (?, 'WORKING', ?, ?)
                    RETURNING branch_id;
                """;

        return jdbcTemplate.queryForObject(sql, UUID.class, pluginId, clientBlobSha, serverBlobSha);
    }

    public List<Branch> findByPlugin(UUID pluginId) {
        String sql = "SELECT " + COLUMNS + " FROM branches WHERE plugin_id = ? ORDER BY created_at DESC;";

        return jdbcTemplate.query(sql, branchRowMapper, pluginId);
    }

    public Optional<Branch> findById(UUID branchId) {
        String sql = "SELECT " + COLUMNS + " FROM branches WHERE branch_id = ?;";

        return jdbcTemplate.query(sql, branchRowMapper, branchId).stream().findFirst();
    }
}
