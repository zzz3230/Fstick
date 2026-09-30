package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.dto.BranchStatus;
import ru.fstick.registry_service.dto.model.QueueItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class QueueItemRowMapper implements RowMapper<QueueItem> {

    @Override
    public QueueItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return QueueItem.builder()
                .pluginId(rs.getObject("plugin_id", UUID.class))
                .pluginName(rs.getString("plugin_name"))
                .branchId(rs.getObject("branch_id", UUID.class))
                .semver(rs.getString("semver"))
                .changelog(rs.getString("changelog"))
                .status(BranchStatus.valueOf(rs.getString("status")))
                .authorId(rs.getObject("author_id", UUID.class))
                .submittedAt(BranchRowMapper.timestamp(rs.getTimestamp("submitted_at")))
                .claimedBy(rs.getObject("claimed_by", UUID.class))
                .build();
    }
}
