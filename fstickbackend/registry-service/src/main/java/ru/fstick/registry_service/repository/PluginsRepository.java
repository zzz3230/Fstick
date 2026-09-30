package ru.fstick.registry_service.repository;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.Status;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.exception.ApiException;
import ru.fstick.registry_service.repository.mapper.*;

import java.util.*;

@Repository
@AllArgsConstructor
public class PluginsRepository {
    private final JdbcTemplate jdbcTemplate;
    private final PluginRowMapper pluginRowMapper;
    private final ScreenshotRowMapper screenshotRowMapper;
    private final CategoryRowMapper categoryRowMapper;

    private static final String PLUGIN_COLUMNS =
            """
                p.plugin_id,
                p.author_id,
                p.name as plugin_name,
                p.description,
                c.name as category_name,
                s.name as status_name,
                p.s3_icon_key,
                p.created_at,
                p.updated_at,
                (SELECT b.branch_id FROM branches b
                  WHERE b.plugin_id = p.plugin_id AND b.status = 'WORKING') as dev_branch_id,
                (SELECT b.semver FROM branches b
                  WHERE b.plugin_id = p.plugin_id AND b.status = 'RELEASED'
                  ORDER BY string_to_array(b.semver, '.')::int[] DESC LIMIT 1) as released_semver,
                (SELECT b.branch_id FROM branches b
                  WHERE b.plugin_id = p.plugin_id AND b.status IN ('WAITING_APPROVE', 'APPROVING')) as candidate_branch_id,
                (SELECT b.semver FROM branches b
                  WHERE b.plugin_id = p.plugin_id AND b.status IN ('WAITING_APPROVE', 'APPROVING')) as candidate_semver,
                (SELECT b.status FROM branches b
                  WHERE b.plugin_id = p.plugin_id AND b.status IN ('WAITING_APPROVE', 'APPROVING')) as candidate_status,
                p.last_rejection->>'reason' as rejection_reason,
                p.last_rejection->>'at' as rejection_at,
                p.last_rejection->>'semver' as rejection_semver,
                (p.last_rejection->>'branch_id')::uuid as rejection_branch_id,
                COALESCE(array_agg(t.name) FILTER (WHERE t.name IS NOT NULL), '{}') as tags
            """;

    private static final String PLUGIN_GROUP_BY =
            """
                p.plugin_id, p.author_id, p.name, p.description,
                c.name, s.name, p.s3_icon_key, p.created_at, p.updated_at, p.last_rejection
            """;

    private static final String PUBLIC_SCOPE =
            """
                s.name = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM branches b WHERE b.plugin_id = p.plugin_id AND b.status = 'RELEASED')
            """;

    private static final String OWNED_SCOPE = "p.author_id = ? AND s.name <> 'DELETED'";

    //сделать запрос в базу данных на поиск плагинов; ownerId == null - публичный каталог
    public List<PluginData> getPlugins(Integer offset, Integer limit, String category, String search, String sort, String order, UUID ownerId) {

        String sql =
        """
            SELECT
                %s
            FROM plugins p
            LEFT JOIN categories c USING(category_id)
            LEFT JOIN statuses s USING(status_id)
            LEFT JOIN plugins_tags pt USING(plugin_id)
            LEFT JOIN tags t USING(tag_id)
            WHERE c.name ILIKE ?
              AND (p.name ILIKE ? OR p.description ILIKE ?)
              AND %s
            GROUP BY
                %s
            ORDER BY %s %s
            LIMIT ? OFFSET ?;
        """;

        //защита от sql инъекции
        List<String> allowedSortFields = List.of("plugin_name", "created_at", "updated_at");

        if (sort == null || !allowedSortFields.contains(sort)) {
            sort = "created_at";
        }

        order = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";

        sql = sql.formatted(PLUGIN_COLUMNS, ownerId == null ? PUBLIC_SCOPE : OWNED_SCOPE, PLUGIN_GROUP_BY, sort, order);

        List<Object> args = new ArrayList<>(List.of("%" + category + "%", "%" + search + "%", "%" + search + "%"));
        if (ownerId != null) {
            args.add(ownerId);
        }
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(sql, pluginRowMapper, args.toArray());
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

        // Fallback: if category not found (empty string or unknown), use the first available category
        if (categoryId == null) {
            List<UUID> anyCategory = jdbcTemplate.query(
                "SELECT category_id FROM categories ORDER BY name LIMIT 1", categoryRowMapper);
            categoryId = anyCategory.isEmpty() ? null : anyCategory.get(0);
        }

        if (categoryId == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "no_categories", "No categories available in the system");
        }

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
    public Optional<PluginData> findPlugin(UUID pluginId) {
        String sql =
                """
                    SELECT
                        %s
                    FROM plugins p
                    JOIN categories c USING(category_id)
                    JOIN statuses s USING(status_id)
                    LEFT JOIN plugins_tags pt USING(plugin_id)
                    LEFT JOIN tags t USING(tag_id)
                    WHERE plugin_id=?
                    GROUP BY
                        %s
                """.formatted(PLUGIN_COLUMNS, PLUGIN_GROUP_BY);

        return jdbcTemplate.query(sql, pluginRowMapper, pluginId).stream().findFirst();
    }

    public PluginData getPlugin(UUID pluginId) {
        return findPlugin(pluginId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "plugin_not_found", "Plugin not found: " + pluginId));
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

    public String getScreenshotKey(UUID pluginId, UUID screenshotId) {
        String sql = """
                    SELECT s3_screenshot_key FROM screenshots
                    WHERE plugin_id=? AND screenshot_id=?;
                """;

        return jdbcTemplate.queryForObject(sql, String.class, pluginId, screenshotId);
    }

    public PluginData addScreenshots(UUID pluginId, List<String> screenshots) {
        if (screenshots != null && !screenshots.isEmpty()) {
            String sqlInsertScreenshots = """
                        INSERT INTO screenshots (plugin_id, s3_screenshot_key)
                        VALUES (?, ?);
                    """;

            for (String screenshotKey : screenshots) {
                jdbcTemplate.update(sqlInsertScreenshots, pluginId, screenshotKey);
            }
        }

        return getPlugin(pluginId);
    }

    public void deleteScreenshot(UUID screenshotId) {
        String deleteScreenshotSql =
                """
                DELETE FROM screenshots WHERE screenshot_id = ?;
                """;

        jdbcTemplate.update(deleteScreenshotSql, screenshotId);
    }

    public void changeStatus(UUID pluginId, Status status) {
        String changeStatusSql =
                """
                    UPDATE plugins SET status_id = (
                        SELECT status_id FROM statuses WHERE name = ?
                    ) WHERE plugin_id = ?;
                """;

        jdbcTemplate.update(changeStatusSql, status.getValue(), pluginId);
    }

    public Integer getPluginsTotal(String category, String search, UUID ownerId) {
        String pluginsTotalSql =
                """
                    SELECT COUNT(DISTINCT p.plugin_id)
                    FROM plugins p
                    LEFT JOIN categories c USING(category_id)
                    LEFT JOIN statuses s USING(status_id)
                    LEFT JOIN plugins_tags pt USING(plugin_id)
                    LEFT JOIN tags t USING(tag_id)
                    WHERE c.name ILIKE ?
                      AND (p.name ILIKE ? OR p.description ILIKE ?)
                      AND %s;
                """.formatted(ownerId == null ? PUBLIC_SCOPE : OWNED_SCOPE);

        List<Object> args = new ArrayList<>(List.of("%" + category + "%", "%" + search + "%", "%" + search + "%"));
        if (ownerId != null) {
            args.add(ownerId);
        }

        return jdbcTemplate.queryForObject(pluginsTotalSql, Integer.class, args.toArray());
    }

    public void setLastRejection(UUID pluginId, String reason, String semver, UUID branchId) {
        String sql =
                """
                    UPDATE plugins
                    SET last_rejection = jsonb_build_object('reason', ?::text, 'at', now(), 'semver', ?::text, 'branch_id', ?::text)
                    WHERE plugin_id = ?;
                """;

        jdbcTemplate.update(sql, reason, semver, branchId.toString(), pluginId);
    }

    public void clearLastRejection(UUID pluginId) {
        jdbcTemplate.update("UPDATE plugins SET last_rejection = NULL WHERE plugin_id = ?;", pluginId);
    }

    public void setIcon(UUID pluginId, String iconKey) {
        jdbcTemplate.update("UPDATE plugins SET s3_icon_key = ? WHERE plugin_id = ?;", iconKey, pluginId);
    }
}
