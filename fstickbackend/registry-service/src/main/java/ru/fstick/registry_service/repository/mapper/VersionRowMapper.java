package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.dto.model.Version;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class VersionRowMapper implements RowMapper<Version> {

    @Override
    public Version mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Version.builder()
                .version(rs.getString("version_number"))
                .changelog(rs.getString("changelog"))
                .versionId(rs.getObject("version_id", UUID.class))
                .createdAt(rs.getString("created_at"))
                .pluginId(rs.getObject("plugin_id", UUID.class))
                .build();
    }
}
