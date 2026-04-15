package ru.fstick.runtimeservice.dto.request;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CommandRequest{
    String name;
    UUID pluginId;
    String chatId;
    String trackId;
    Map<String, Object> args;
}