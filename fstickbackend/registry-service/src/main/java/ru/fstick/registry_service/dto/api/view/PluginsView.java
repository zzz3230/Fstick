package ru.fstick.registry_service.dto.api.view;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.model.PluginData;
import ru.fstick.registry_service.dto.service.PaginationData;

import java.util.List;

@Data
@Builder
public class PluginsView {
    private List<PluginViewShrink> items;
    private PaginationData pagination;
}
