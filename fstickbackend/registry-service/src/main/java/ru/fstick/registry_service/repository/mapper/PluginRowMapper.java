package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
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
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime().toString())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime().toString())
                .build();
    }
}