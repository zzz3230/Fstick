package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.dto.api.view.CandidateView;
import ru.fstick.registry_service.dto.api.view.LastRejectionView;
import ru.fstick.registry_service.dto.model.PluginData;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class PluginRowMapper implements RowMapper<PluginData> {

    @Override
    public PluginData mapRow(ResultSet rs, int rowNum) throws SQLException {
        Array sqlArray = rs.getArray("tags");
        List<String> tags = sqlArray != null
                ? Arrays.asList((String[]) sqlArray.getArray())
                : List.of();
        return PluginData.builder()
                .id(rs.getObject("plugin_id", UUID.class))
                .authorId(rs.getObject("author_id", UUID.class))
                .name(rs.getString("plugin_name"))
                .category(rs.getString("category_name"))
                .status(rs.getString("status_name"))
                .tags(tags)
                .description(rs.getString("description"))
                .iconUrlKey(rs.getString("s3_icon_key"))
                .devBranchId(rs.getObject("dev_branch_id", UUID.class))
                .releasedSemver(rs.getString("released_semver"))
                .candidate(candidate(rs))
                .lastRejection(lastRejection(rs))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime().toString())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime().toString())
                .build();
    }

    private static CandidateView candidate(ResultSet rs) throws SQLException {
        UUID branchId = rs.getObject("candidate_branch_id", UUID.class);
        if (branchId == null) {
            return null;
        }
        return CandidateView.builder()
                .branchId(branchId)
                .semver(rs.getString("candidate_semver"))
                .status(rs.getString("candidate_status"))
                .build();
    }

    private static LastRejectionView lastRejection(ResultSet rs) throws SQLException {
        String reason = rs.getString("rejection_reason");
        UUID branchId = rs.getObject("rejection_branch_id", UUID.class);
        if (reason == null && branchId == null) {
            return null;
        }
        return LastRejectionView.builder()
                .reason(reason)
                .at(rs.getString("rejection_at"))
                .semver(rs.getString("rejection_semver"))
                .branchId(branchId)
                .build();
    }
}