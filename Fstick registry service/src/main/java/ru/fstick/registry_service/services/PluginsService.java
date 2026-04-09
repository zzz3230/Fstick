package ru.fstick.registry_service.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.fstick.registry_service.dto.api.requests.PluginRequest;
import ru.fstick.registry_service.dto.api.responsies.PluginResponse;
import ru.fstick.registry_service.dto.bd.Screenshot;
import ru.fstick.registry_service.dto.bd.Version;
import ru.fstick.registry_service.dto.service.PaginationData;
import ru.fstick.registry_service.dto.bd.PluginData;
import ru.fstick.registry_service.dto.api.responsies.PluginsResponse;
import ru.fstick.registry_service.repositories.PluginsRepositoryMock;

import java.util.List;

@Service
public class PluginsService {

    private final PluginsRepositoryMock pluginsRepositoryMock;

    public PluginsService(PluginsRepositoryMock pluginsRepositoryMock) {
        this.pluginsRepositoryMock = pluginsRepositoryMock;
    }

    //получить список плагинов и их пагинацию по критериям
    public PluginsResponse getPlugins(Integer page, Integer limit, String category, String search, String sort, String order) {
        Integer offset = page * limit;
        List<PluginData> pluginsData = pluginsRepositoryMock.getPlugins(offset, limit, category, search, sort, order);
        Integer pluginsTotal = pluginsRepositoryMock.countPlugins(category, search);

        int totalPages = (int) Math.ceil((double) pluginsTotal / limit);

        PaginationData paginationData = PaginationData.builder()
                .page(page)
                .limit(limit)
                .total(pluginsTotal)
                .totalPages(totalPages)
                .hasNext(page<totalPages-1)
                .hasPrev(page>0)
                .build();

        return PluginsResponse.builder()
                .items(pluginsData)
                .pagination(paginationData)
                .build();
    }


    //добавить плагин
    public PluginResponse addPlugin(PluginRequest pluginRequest, MultipartFile archive, MultipartFile icon, List<MultipartFile> screenshots) {

        PluginData pluginData = pluginsRepositoryMock.addPlugin(
                pluginRequest.getName(),
                pluginRequest.getDescription(),
                pluginRequest.getCategory(),
                pluginRequest.getTags(),
                pluginRequest.getVersion(),
                pluginRequest.getChangelog());

        List<Version> versions = pluginsRepositoryMock.getVersionOfPlugin(pluginData.getId());
        List<Screenshot> screenshotsData = pluginsRepositoryMock.getScreenshots(pluginData.getId());


        return PluginResponse.builder()
                .id(pluginData.getId())
                .creatorId(pluginData.getCreatorId())
                .name(pluginData.getName())
                .description(pluginData.getDescription())
                .category(pluginData.getCategory())
                .tags(pluginRequest.getTags())
                .currentVersion(pluginData.getCurrentVersion())
                .status(pluginData.getStatus())
                .iconUrl(pluginData.getIconUrlKey())
                .createdAt(pluginData.getCreatedAt())
                .updatedAt(pluginData.getUpdatedAt())
                .versions(versions)
                .screenshots(screenshotsData)
                .build();
    }

}
