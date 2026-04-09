package ru.fstick.registry_service.dto.api.responsies;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.bd.PluginData;
import ru.fstick.registry_service.dto.service.PaginationData;

import java.util.List;

@Data
@Builder
public class PluginsResponse {
    private List<PluginData> items;
    private PaginationData pagination;
}
