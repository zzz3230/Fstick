package ru.fstick.runtimeservice.lang;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PluginRuntimeContext {
    private PluginRuntimeState state;
    private String chatId;
    private String senderId;
}
