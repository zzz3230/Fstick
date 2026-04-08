package ru.fstick.registry_service.services;

import org.springframework.stereotype.Service;
import ru.fstick.registry_service.dto.api.PaginationData;
import ru.fstick.registry_service.dto.bd.PluginData;
import ru.fstick.registry_service.dto.api.PluginsResponse;
import ru.fstick.registry_service.repositories.PluginsRepository;

import java.util.List;

@Service
public class PluginsService {

    private final PluginsRepository pluginsRepository;

    public PluginsService(PluginsRepository pluginsRepository) {
        this.pluginsRepository = pluginsRepository;
    }

    //получить список плагинов и их пагинацию по критериям
    public PluginsResponse getPlugins(Integer page, Integer limit, String category, String search, String sort, String order) {
        Integer offset = page * limit;
        List<PluginData> pluginsData = pluginsRepository.getPlugins(offset, limit, category, search, sort, order);
        Integer pluginsTotal = pluginsRepository.countPlugins(category, search);

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
}
