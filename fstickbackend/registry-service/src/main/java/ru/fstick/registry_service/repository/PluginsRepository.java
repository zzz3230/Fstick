package ru.fstick.registry_service.repository;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;
import ru.fstick.registry_service.repository.mapper.*;

import java.time.LocalDateTime;
import java.util.*;

@Repository
@AllArgsConstructor
public class PluginsRepository {
    private final JdbcTemplate jdbcTemplate;
    private final PluginRowMapper pluginRowMapper;
    private final VersionRowMapper versionRowMapper;
    private final ScreenshotRowMapper screenshotRowMapper;
    private final CategoryRowMapper categoryRowMapper;

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
            LEFT JOIN categories c USING(category_id)
            LEFT JOIN statuses s USING(status_id)
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

    public void addPlugin(UUID pluginId, @NotBlank String name, @NotBlank String description, String category, List<String> tags, UUID authorId) {

        String sqlCategory =
        """
            SELECT categories.category_id as category_id
            FROM categories
            WHERE categories.name=?;
        """;

        List<UUID> categoryTemp = jdbcTemplate.query(sqlCategory, categoryRowMapper, category);
        UUID categoryId = categoryTemp.isEmpty() ? null : categoryTemp.get(0);

        String sqlStatus =
                """
                    SELECT statuses.status_id as status_id
                    FROM statuses
                    WHERE statuses.name=?;
                """;

        UUID statusId = jdbcTemplate.queryForObject(sqlStatus, UUID.class, Status.ACTIVE.getValue());

        String sqlPluginInsert =
        """
            INSERT INTO plugins (plugin_id, name, description, category_id, status_id, author_id)
            VALUES (?, ?, ?, ?, ?, ?);
        """;

        jdbcTemplate.update(sqlPluginInsert, pluginId, name, description, categoryId, statusId, authorId);

        if (tags != null && !tags.isEmpty()) {
            String sqlTagsInsert =
                    """
                        INSERT INTO tags (name) VALUES (?)
                        ON CONFLICT (name) DO NOTHING;
                    """;

            jdbcTemplate.batchUpdate(sqlTagsInsert, tags, tags.size(), (ps, arg) -> ps.setString(1, arg));

            String inSql = String.join(",", Collections.nCopies(tags.size(), "?"));

            String sqlTags =
                    "SELECT tag_id FROM tags WHERE name IN (" + inSql + ")";

            List<UUID> tagIds = jdbcTemplate.queryForList(sqlTags, UUID.class, tags.toArray());

            String sqlTagsPluginsInsert =
                    """
                        INSERT INTO plugins_tags (plugin_id, tag_id) VALUES (?, ?)
                        ON CONFLICT DO NOTHING;
                    """;


            jdbcTemplate.batchUpdate(sqlTagsPluginsInsert, tagIds, tagIds.size(), (ps, arg) -> {
                ps.setObject(1, pluginId);
                ps.setObject(2, arg);
            });
        }
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
        if (category != null && !category.isBlank()) {
            String sqlCategory =
                    """
                        SELECT categories.category_id as category_id
                        FROM categories
                        WHERE categories.name=?;
                    """;

            List<UUID> categoryTemp = jdbcTemplate.query(sqlCategory, categoryRowMapper, category);
            UUID categoryId = categoryTemp.isEmpty() ? null : categoryTemp.get(0);

            String sql =
                    """
                        UPDATE plugins SET name=?, description=?, category_id=? WHERE plugin_id=?;
                    """;

            jdbcTemplate.update(sql, name, description, categoryId, pluginId);
        } else {
            String sql =
                    """
                        UPDATE plugins SET name=?, description=? WHERE plugin_id=?;
                    """;

            jdbcTemplate.update(sql, name, description, pluginId);
        }

        if (tags != null) {
            String deleteTagsSql =
                    """
                        DELETE FROM plugins_tags WHERE plugin_id=?;
                    """;

            jdbcTemplate.update(deleteTagsSql, pluginId);

            if (!tags.isEmpty()) {
                List<String> distinctTags = tags.stream().distinct().toList();

                String sqlTagsInsert =
                        """
                            INSERT INTO tags (name) VALUES (?)
                            ON CONFLICT (name) DO NOTHING;
                        """;

                jdbcTemplate.batchUpdate(sqlTagsInsert, distinctTags, distinctTags.size(), (ps, arg) -> ps.setString(1, arg));

                String inSql = String.join(",", Collections.nCopies(distinctTags.size(), "?"));

                String sqlTags =
                        "SELECT tag_id FROM tags WHERE name IN (" + inSql + ")";

                List<UUID> tagIds = jdbcTemplate.queryForList(sqlTags, UUID.class, distinctTags.toArray());

                String sqlTagsPluginsInsert =
                        """
                            INSERT INTO plugins_tags (plugin_id, tag_id) VALUES (?, ?)
                            ON CONFLICT DO NOTHING;
                        """;

                jdbcTemplate.batchUpdate(sqlTagsPluginsInsert, tagIds, tagIds.size(), (ps, arg) -> {
                    ps.setObject(1, pluginId);
                    ps.setObject(2, arg);
                });
            }
        }

        return getPlugin(pluginId);
    }


    //удалить плагин
    public PluginData deletePlugin(UUID pluginId) {
        String statusSql = """
                    SELECT statuses.status_id as status_id
                    FROM statuses
                    WHERE statuses.name=?;
                """;

        UUID statusId = jdbcTemplate.queryForObject(statusSql, UUID.class, Status.DELETED.getValue());

        String sql = """
                    UPDATE plugins SET status_id=? WHERE plugin_id=?;
                """;

        jdbcTemplate.update(sql, statusId, pluginId);

        return getPlugin(pluginId);
    }

    //создать версию плагина
    public UUID createVersion(UUID pluginId, String versionNumber, String changelog) {
        String sql = """
                    INSERT INTO versions (version_number, changelog, plugin_id)
                    VALUES (?, ?, ?)
                    RETURNING version_id;
                """;

        return jdbcTemplate.queryForObject(sql, UUID.class, versionNumber, changelog, pluginId);
    }

    public PluginData addPluginVersion(UUID pluginId, List<String> files) {




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

    public List<String> getCodeServer() {
        //TODO
        return null;
    }

    public PluginData addFiles(UUID pluginId, String icon, List<String> allScreenshots, List<String> allFiles) {
        // Обновить иконку плагина
        if (icon != null && !icon.isBlank()) {
            String sqlUpdateIcon = """
                        UPDATE plugins SET s3_icon_key = ? WHERE plugin_id = ?;
                    """;
            jdbcTemplate.update(sqlUpdateIcon, icon, pluginId);
        }

        // Добавить скриншоты
        if (allScreenshots != null && !allScreenshots.isEmpty()) {
            String sqlInsertScreenshots = """
                        INSERT INTO screenshots (plugin_id, s3_screenshot_key)
                        VALUES (?, ?);
                    """;

            for (String screenshotKey : allScreenshots) {
                jdbcTemplate.update(sqlInsertScreenshots, pluginId, screenshotKey);
            }
        }

        // Добавить файлы кода к последней версии
        if (allFiles != null && !allFiles.isEmpty()) {
            String sqlInsertFiles = """
                        INSERT INTO files (version_id, s3_file_key)
                        SELECT v.version_id, ? FROM versions v
                        WHERE v.plugin_id = ?
                        ORDER BY v.created_at DESC
                        LIMIT 1;
                    """;

            for (String fileKey : allFiles) {
                jdbcTemplate.update(sqlInsertFiles, fileKey, pluginId);
            }
        }

        return getPlugin(pluginId);
    }

    //перегруженный метод для добавления только файлов кода к версии
    public PluginData addFiles(UUID pluginId, List<String> files) {
        if (files != null && !files.isEmpty()) {
            String sqlInsertFiles = """
                        INSERT INTO files (version_id, s3_file_key)
                        SELECT v.version_id, ? FROM versions v
                        WHERE v.plugin_id = ?
                        ORDER BY v.created_at DESC
                        LIMIT 1;
                    """;

            for (String fileKey : files) {
                jdbcTemplate.update(sqlInsertFiles, fileKey, pluginId);
            }
        }

        return getPlugin(pluginId);
    }
}
