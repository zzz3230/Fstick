package ru.fstick.registry_service.repository;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;
import ru.fstick.registry_service.repository.mapper.PluginRowMapper;
import ru.fstick.registry_service.repository.mapper.TagRowMapper;

import java.time.LocalDateTime;
import java.util.*;

@Repository
@AllArgsConstructor
public class PluginsRepository {
    private final JdbcTemplate jdbcTemplate;
    private final PluginRowMapper pluginRowMapper;
    private final TagRowMapper tagRowMapper;

    //сделать запрос в базу данных на поиск плагинов
    public List<PluginData> getPlugins(Integer offset, Integer limit, String category, String search, String sort, String order) {

        String sql =
        """
            SELECT
                p.plugin_id,
                p.author_id,
                p.name as plugin_name,
                p.description,
                c.name as category_name,
                s.name as status_name,
                p.s3_icon_key,
                p.created_at,
                p.updated_at,
                COALESCE(array_agg(t.name) FILTER (WHERE t.name IS NOT NULL), '{}') as tags
            FROM plugins p
            JOIN categories c USING(category_id)
            JOIN statuses s USING(status_id)
            LEFT JOIN plugins_tags pt USING(plugin_id)
            LEFT JOIN tags t USING(tag_id)
            WHERE c.name ILIKE ?
              AND (p.name ILIKE ? OR p.description ILIKE ?)
            GROUP BY
                p.plugin_id, p.author_id, p.name, p.description,
                c.name, s.name, p.s3_icon_key, p.created_at, p.updated_at
            ORDER BY %s %s
            LIMIT ? OFFSET ?;
        """;

        //защита от sql инъекции
        List<String> allowedSortFields = List.of("plugin_name", "created_at", "updated_at");

        if (sort == null || !allowedSortFields.contains(sort)) {
            sort = "created_at";
        }

        order = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";

        sql = sql.formatted(sort, order);

        List<PluginData> pluginsData = jdbcTemplate.query(sql, pluginRowMapper, "%" + category + "%", "%" + search + "%", "%" + search + "%", limit, offset);

        return pluginsData;
    }

    //сделать запрос в базу данных на количество плагинов
    public Integer countPlugins(String category, String search) {
        return 20;
        //TODO
    }

    public PluginData addPlugin(UUID pluginId, @NotBlank String name, @NotBlank String description, String category,List<String> keys , List<String> tags) {
        return PluginData.builder()
                .id(pluginId)
                .creatorId(UUID.randomUUID())
                .name("test")
                .description("mock")
                .category("social")
                .tags(new ArrayList<>(Arrays.asList("tag1", "tag2", "tag3")))
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
        //TODO
    }


    //Получить версии плагина
    public List<Version> getVersionOfPlugin(UUID id) {
        return new ArrayList<>(Arrays.asList(Version.builder()
                .id(UUID.randomUUID())
                .version("1.0.0")
                .changelog("created")
                .createdAt(LocalDateTime.now().toString()).build()));
        //TODO
    }

    //Получить скриншоты плагина
    public List<Screenshot> getScreenshots(UUID id) {
        return new ArrayList<>(Arrays.asList(Screenshot.builder()
                .screenshotId(UUID.randomUUID())
                .s3ScreenshotKey("s3key")
                .build()));
        //TODO
    }

    //Получить плагин по id
    public PluginData getPlugin(UUID pluginId) {
        return PluginData.builder()
                .id(pluginId)
                .creatorId(UUID.randomUUID())
                .name("test")
                .description("mock")
                .category("social")
                .tags(new ArrayList<>(Arrays.asList("tag1", "tag2", "tag3")))
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
        //TODO
    }


    //обновить метаданные плагина
    public PluginData updatePlugin(UUID pluginId, @NotBlank String name, @NotBlank String description, String category, List<String> tags) {
        return PluginData.builder()
                .id(pluginId)
                .creatorId(UUID.randomUUID())
                .name(name)
                .description(description)
                .category(category)
                .tags(tags)
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
        //TODO
    }


    //удалить плагин
    public PluginData deletePlugin(UUID pluginId) {
        return PluginData.builder()
                .id(pluginId)
                .creatorId(UUID.randomUUID())
                .name("test")
                .description("mock")
                .category("social")
                .tags(new ArrayList<>(Arrays.asList("tag1", "tag2", "tag3")))
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
        //TODO
    }

    public PluginData addPluginVersion(UUID pluginId) {
        return PluginData.builder()
                .id(pluginId)
                .creatorId(UUID.randomUUID())
                .name("test")
                .description("mock")
                .category("social")
                .tags(new ArrayList<>(Arrays.asList("tag1", "tag2", "tag3")))
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
        //TODO
    }

    public String getAssetKey(UUID assetId) {
        //TODO
        return null;
    }

    public List<String> getCodeClient() {
        //TODO
        return null;
    }

    public List<String> getServerClient() {
        //TODO
        return null;
    }
}
