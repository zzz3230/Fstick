package ru.fstick.registry_service.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.model.Branch;
import ru.fstick.registry_service.dto.model.QueueItem;
import ru.fstick.registry_service.repository.mapper.BranchRowMapper;
import ru.fstick.registry_service.repository.mapper.QueueItemRowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class BranchRepository {

    private static final String COLUMNS =
            """
                branch_id, plugin_id, status, semver, client_blob_sha, server_blob_sha,
                runtime_client, runtime_server, base_branch_id, created_at,
                changelog, submitted_at, claimed_by, reject_reason
            """;

    private final JdbcTemplate jdbcTemplate;
    private final BranchRowMapper branchRowMapper;
    private final QueueItemRowMapper queueItemRowMapper;

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

    public Optional<Branch> lockForUpdate(UUID branchId) {
        String sql = "SELECT " + COLUMNS + " FROM branches WHERE branch_id = ? FOR UPDATE;";

        return jdbcTemplate.query(sql, branchRowMapper, branchId).stream().findFirst();
    }

    public Optional<Branch> findOpenCandidate(UUID pluginId) {
        String sql = "SELECT " + COLUMNS
                + " FROM branches WHERE plugin_id = ? AND status IN ('WAITING_APPROVE', 'APPROVING');";

        return jdbcTemplate.query(sql, branchRowMapper, pluginId).stream().findFirst();
    }

    public List<String> findReleasedSemvers(UUID pluginId) {
        String sql = "SELECT semver FROM branches WHERE plugin_id = ? AND status = 'RELEASED';";

        return jdbcTemplate.queryForList(sql, String.class, pluginId);
    }

    public UUID createCandidate(Branch source, String semver, String changelog) {
        String sql =
                """
                    INSERT INTO branches (plugin_id, status, semver, client_blob_sha, server_blob_sha,
                                          runtime_client, runtime_server, base_branch_id, changelog, submitted_at)
                    VALUES (?, 'WAITING_APPROVE', ?, ?, ?, ?, ?, ?, ?, now())
                    RETURNING branch_id;
                """;

        return jdbcTemplate.queryForObject(sql, UUID.class, source.getPluginId(), semver,
                source.getClientBlobSha(), source.getServerBlobSha(),
                source.getRuntimeClient(), source.getRuntimeServer(), source.getId(), changelog);
    }

    public void markClaimed(UUID branchId, UUID moderatorId) {
        String sql = "UPDATE branches SET status = 'APPROVING', claimed_by = ? WHERE branch_id = ?;";

        jdbcTemplate.update(sql, moderatorId, branchId);
    }

    public void markReleased(UUID branchId) {
        jdbcTemplate.update("UPDATE branches SET status = 'RELEASED' WHERE branch_id = ?;", branchId);
    }

    public void markRejected(UUID branchId, String reason) {
        String sql = "UPDATE branches SET status = 'REJECTED', reject_reason = ? WHERE branch_id = ?;";

        jdbcTemplate.update(sql, reason, branchId);
    }

    public void markCancelled(UUID branchId) {
        jdbcTemplate.update("UPDATE branches SET status = 'CANCELLED' WHERE branch_id = ?;", branchId);
    }

    public List<QueueItem> findQueue(List<BranchStatus> statuses, int offset, int limit) {
        String sql =
                """
                    SELECT b.plugin_id, p.name AS plugin_name, b.branch_id, b.semver, b.changelog, b.status,
                           p.author_id, b.submitted_at, b.claimed_by
                    FROM branches b
                    JOIN plugins p USING(plugin_id)
                    WHERE b.status IN (%s)
                    ORDER BY b.submitted_at ASC, b.branch_id ASC
                    LIMIT ? OFFSET ?;
                """.formatted(placeholders(statuses));

        List<Object> args = new ArrayList<>(statusNames(statuses));
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(sql, queueItemRowMapper, args.toArray());
    }

    public int countQueue(List<BranchStatus> statuses) {
        String sql = "SELECT COUNT(*) FROM branches WHERE status IN (" + placeholders(statuses) + ");";

        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, statusNames(statuses).toArray());
        return total == null ? 0 : total;
    }

    private static List<String> statusNames(List<BranchStatus> statuses) {
        return statuses.stream().map(Enum::name).toList();
    }

    private static String placeholders(List<BranchStatus> statuses) {
        return String.join(", ", Collections.nCopies(statuses.size(), "?"));
    }

    public void updateShas(UUID branchId, String clientBlobSha, String serverBlobSha) {
        String sql = "UPDATE branches SET client_blob_sha = ?, server_blob_sha = ? WHERE branch_id = ?;";

        jdbcTemplate.update(sql, clientBlobSha, serverBlobSha, branchId);
    }
}
