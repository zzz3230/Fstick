package ru.fstick.registry_service.repository.mapper;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;


@Component
public class CategoryRowMapper implements RowMapper<UUID> {


    @Override
    public UUID mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getObject("category_id", UUID.class);
    }
}
