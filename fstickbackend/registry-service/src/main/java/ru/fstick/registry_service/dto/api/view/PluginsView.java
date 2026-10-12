package ru.fstick.registry_service.dto.api.view;


import lombok.Builder;
import lombok.Data;
import ru.fstick.registry_service.dto.service.PaginationData;

import java.util.List;

@Data
@Builder
public class PluginsView<T> {
    private List<T> items;
    private PaginationData pagination;
}
