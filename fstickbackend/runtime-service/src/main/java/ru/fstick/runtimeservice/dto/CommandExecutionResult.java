package ru.fstick.runtimeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CommandExecutionResult {
    CommandStatus status;
    Object response;
    CommandError error; // null, если нет ошибки
    Object state;       // актуальный стейт плагина после выполнения команды (может быть null)
}
