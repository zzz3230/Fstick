package ru.fstick.runtimeservice.lang;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class PluginRuntimeState {
    @Setter
    private Object value;
    private UUID ownerPluginId;
    private String ownerChatId;
}
