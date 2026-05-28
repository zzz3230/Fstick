package ru.fstick.runtimeservice.lang;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PluginRuntimeContext {
    /** Единый стейт чата (содержит user_scoped поля как userId→data) */
    private PluginRuntimeState state;
    private String chatId;
    private String senderId;
}
