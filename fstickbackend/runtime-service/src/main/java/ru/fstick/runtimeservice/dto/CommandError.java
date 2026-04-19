package ru.fstick.runtimeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CommandError {
    String message;       // что произошло
    String code;             // код ошибки плагина, если есть
    String details;        // стек, внутренние данные, опционально
}
