package ru.fstick.registry_service.repository;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;
import ru.fstick.registry_service.repository.mapper.PluginRowMapper;
import ru.fstick.registry_service.repository.mapper.ScreenshotRowMapper;
import ru.fstick.registry_service.repository.mapper.TagRowMapper;
import ru.fstick.registry_service.repository.mapper.VersionRowMapper;

import java.time.LocalDateTime;
import java.util.*;

@Repository
@AllArgsConstructor
public class PluginsRepository {
    private final JdbcTemplate jdbcTemplate;
    private final PluginRowMapper pluginRowMapper;
    private final TagRowMapper tagRowMapper;
    private final VersionRowMapper versionRowMapper;
    private final ScreenshotRowMapper screenshotRowMapper;

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

        return jdbcTemplate.query(sql, pluginRowMapper, "%" + category + "%", "%" + search + "%", "%" + search + "%", limit, offset);
    }

    public PluginData addPlugin(UUID pluginId, @NotBlank String name, @NotBlank String description, String category, List<String> tags, UUID authorId, String iconKey, List<String> screenshotKeys, List<String> fileKeys) {

        String sqlCategory =
        """
            SELECT categories.category_id as category_id
            FROM categories
            WHERE categories.name=?;
        """;

        UUID categoryId = jdbcTemplate.queryForObject(sqlCategory, UUID.class, category);

        String sqlStatus =
                """
                    SELECT statuses.status_id as status_id
                    FROM statuses
                    WHERE statuses.name=?;
                """;

        UUID statusId = jdbcTemplate.queryForObject(sqlStatus, UUID.class, Status.ACTIVE);


        String sqlPluginInsert =
        """
            INSERT INTO plugins (plugin_id, name, description, category_id, status_id, author_id, s3_icon_key)
            VALUES (?, ?, ?, ?, ?, ?, ?);
        """;

        jdbcTemplate.update(sqlPluginInsert, pluginId, name, description, categoryId, statusId, authorId, iconKey);

        return PluginData.builder()
                .id(pluginId)
                .authorId(UUID.randomUUID())
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
    public List<Version> getVersionsOfPlugin(UUID pluginId) {
        String sql =
        """
            SELECT version_id as version_id,
                   plugin_id as plugin_id,
                   changelog as changelog,
                   version_number as version_number,
                   created_at as created_at
            FROM versions
            WHERE plugin_id=?;
        """;

        return jdbcTemplate.query(sql, versionRowMapper, pluginId);
    }

    //Получить скриншоты плагина
    public List<Screenshot> getScreenshots(UUID pluginId) {
        String sql =
                """
                    SELECT screenshot_id as screenshot_id,
                           plugin_id as plugin_id,
                           s3_screenshot_key as s3_screenshot_key
                    FROM screenshots
                    WHERE plugin_id=?;
                """;

        return jdbcTemplate.query(sql, screenshotRowMapper, pluginId);
    }

    //Получить плагин по id
    public PluginData getPlugin(UUID pluginId) {
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
                    WHERE plugin_id=?
                    GROUP BY
                        p.plugin_id, p.author_id, p.name, p.description,
                        c.name, s.name, p.s3_icon_key, p.created_at, p.updated_at
                """;

        return jdbcTemplate.query(sql, pluginRowMapper, pluginId).get(0);
    }


    //обновить метаданные плагина
    public PluginData updatePlugin(UUID pluginId, @NotBlank String name, @NotBlank String description, String category, List<String> tags) {
        return PluginData.builder()
                .id(pluginId)
                .authorId(UUID.randomUUID())
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
                .authorId(UUID.randomUUID())
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
                .authorId(UUID.randomUUID())
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
