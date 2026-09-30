package ru.fstick.installationservice.repository;

import org.springframework.jdbc.core.RowMapper;
import ru.fstick.installationservice.entity.Installation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class InstallationRowMapper implements RowMapper<Installation> {

    @Override
    public Installation mapRow(ResultSet rs, int rowNum) throws SQLException {
        Installation installation = new Installation();

        installation.setInstallationId(UUID.fromString(rs.getString("installation_id")));
        installation.setPluginId(UUID.fromString(rs.getString("plugin_id")));
        installation.setBranchId(UUID.fromString(rs.getString("branch_id")));
        installation.setBranchStatus(rs.getString("branch_status"));
        installation.setPluginAuthorId(UUID.fromString(rs.getString("plugin_author_id")));
        installation.setChatId(rs.getString("chat_id"));
        installation.setInstalledBy(rs.getObject("installed_by", UUID.class));
        installation.setInstalledAt(rs.getObject("installed_at", java.time.OffsetDateTime.class));
        installation.setUpdatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class));

        return installation;
    }
}