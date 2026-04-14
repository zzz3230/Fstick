package ru.fstick.registry_service.util;

import lombok.Getter;

@Getter
public enum KeyType {
    SCREENSHOT("screenshot"),
    ICON("icon"),
    FILE("file"),
    UNDEFINED("undefined");

    private final String value;

    KeyType(String value) {
        this.value = value;
    }

}