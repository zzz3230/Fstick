package ru.fstick.registry_service.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.api.PaginationData;
import ru.fstick.registry_service.dto.bd.PluginData;

import java.util.ArrayList;
import java.util.List;
@Repository
public class PluginsRepository {
    private final JdbcTemplate jdbcTemplate;

    public PluginsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    //сделать запрос в базу данных на поиск плагинов
    public List<PluginData> getPlugins(Integer offset, Integer limit, String category, String search, String sort, String order) {
       return new ArrayList<>();
       //TODO
    }

    //сделать запрос в базу данных на
    public Integer countPlugins(String category, String search) {
        return 0;
        //TODO
    }
}
