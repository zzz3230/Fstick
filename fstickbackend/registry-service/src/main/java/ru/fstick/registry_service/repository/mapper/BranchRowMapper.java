package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.model.Branch;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class BranchRowMapper implements RowMapper<Branch> {

    @Override
    public Branch mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Branch.builder()
                .id(rs.getObject("branch_id", UUID.class))
                .pluginId(rs.getObject("plugin_id", UUID.class))
                .status(BranchStatus.valueOf(rs.getString("status")))
                .semver(rs.getString("semver"))
                .clientBlobSha(rs.getString("client_blob_sha"))
                .serverBlobSha(rs.getString("server_blob_sha"))
                .runtimeClient(rs.getString("runtime_client"))
                .runtimeServer(rs.getString("runtime_server"))
                .baseBranchId(rs.getObject("base_branch_id", UUID.class))
                .changelog(rs.getString("changelog"))
                .submittedAt(timestamp(rs.getTimestamp("submitted_at")))
                .claimedBy(rs.getObject("claimed_by", UUID.class))
                .rejectReason(rs.getString("reject_reason"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime().toString())
                .build();
    }

    static String timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }
}
