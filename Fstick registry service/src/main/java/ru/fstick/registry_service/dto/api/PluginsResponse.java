package ru.fstick.registry_service.dto.api;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.bd.PluginData;

import java.util.List;

@Data
@Builder
public class PluginsResponse {
    private List<PluginData> items;
    private PaginationData pagination;
}
