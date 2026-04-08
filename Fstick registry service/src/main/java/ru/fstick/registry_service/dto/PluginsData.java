package ru.fstick.registry_service.dto;

import com.sun.source.util.Plugin;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PluginsData {
    private List<PluginData> plugins;
}
