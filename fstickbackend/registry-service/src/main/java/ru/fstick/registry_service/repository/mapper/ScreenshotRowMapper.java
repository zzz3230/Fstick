package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.fstick.registry_service.dto.model.Screenshot;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Component
public class ScreenshotRowMapper implements RowMapper<Screenshot> {
    @Override
    public Screenshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Screenshot.builder()
                .screenshotId(rs.getObject("screenshot_id", UUID.class))
                .pluginId(rs.getObject("plugin_id", UUID.class))
                .s3ScreenshotKey(rs.getString("s3_screenshot_key"))
                .build();
    }
}
