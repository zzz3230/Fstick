package ru.fstick.runtimeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.fstick.runtimeservice.controller.PluginExecuteController;

@AllArgsConstructor
@Data
public class CommandExecutionResult {
    CommandStatus status;
    Object response;
    CommandError error; // null, если нет ошибки
}
