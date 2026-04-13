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

    private List<String> getTags(UUID pluginId) {
        String sql =
        """
            SELECT tags.name as tag_name
            FROM tags
            JOIN plugins_tags USING(tag_id)
            WHERE plugin_id = ?
        """;

        return jdbcTemplate.query(sql, tagRowMapper, pluginId);
    }

    //сделать запрос в базу данных на поиск плагинов
    //ТУТ N+1 ПРОБЛЕМА С ТЭГАМИ, НАДО РЕШИТЬ ПО ХОРОШЕМУ
    public List<PluginData> getPlugins(Integer offset, Integer limit, String category, String search, String sort, String order) {

        String sql =
        """
            SELECT
                plugin_id,
                author_id,
                plugins.name as plugin_name,
                description,
                categories.name as category_name,
                statuses.name as status_name,
                s3_icon_key,
                created_at,
                updated_at
            FROM plugins
            JOIN categories USING(category_id)
            JOIN statuses USING(status_id)
            WHERE categories.name ILIKE ? AND (plugins.name ILIKE ? OR plugins.description ILIKE ?)
            ORDER BY %s %s
            LIMIT ? OFFSET ?
        """;

        //защита от sql инъекции
        List<String> allowedSortFields = List.of("plugin_name", "created_at", "updated_at");

        if (sort == null || !allowedSortFields.contains(sort)) {
            sort = "created_at";
        }

        order = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";

        sql = sql.formatted(sort, order);

        List<PluginData> pluginsData = jdbcTemplate.query(sql, pluginRowMapper, "%" + category + "%", "%" + search + "%", "%" + search + "%", limit, offset);

        pluginsData.forEach(pluginData -> pluginData.setTags(getTags(pluginData.getId())));

        System.out.println(pluginsData);
        System.out.println(offset);
        System.out.println(limit);
        System.out.println(category);
        System.out.println(search);

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
