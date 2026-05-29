package ru.fstick.runtimeservice.lang;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class PluginRuntimeContext {
    /** Единый стейт чата (содержит user_scoped поля как userId→data) */
    private UUID pluginId;
    private PluginRuntimeState state;
    private String chatId;
    private String senderId;
}
