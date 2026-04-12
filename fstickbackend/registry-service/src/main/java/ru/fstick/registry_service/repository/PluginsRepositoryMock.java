package ru.fstick.registry_service.repository;

import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Repository;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.model.Screenshot;
import ru.fstick.registry_service.dto.model.Version;

import java.time.LocalDateTime;
import java.util.*;

@Repository
public class PluginsRepositoryMock {
    /*private final JdbcTemplate jdbcTemplate;

    public PluginsRepositoryMock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }*/

    //сделать запрос в базу данных на поиск плагинов
    public List<PluginData> getPlugins(Integer offset, Integer limit, String category, String search, String sort, String order) {

        PluginData pluginData = PluginData.builder()
                .id(UUID.randomUUID())
                .creatorId(UUID.randomUUID())
                .name("test")
                .description("mock")
                .category("social")
                .tags(new ArrayList<>(Arrays.asList("tag1", "tag2", "tag3")))
                .status("active").iconUrlKey("iconurls3key")
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();

        return new ArrayList<>(Collections.singletonList(pluginData));
        //TODO
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
}
