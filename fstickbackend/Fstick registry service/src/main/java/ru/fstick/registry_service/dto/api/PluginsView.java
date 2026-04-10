package ru.fstick.registry_service.dto.api;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.service.PaginationData;

import java.util.List;

@Data
@Builder
public class PluginsView {
    private List<PluginData> items;
    private PaginationData pagination;
}
