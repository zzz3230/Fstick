package ru.fstick.registry_service.dto;

import lombok.Getter;

@Getter
public enum Status {
    ACTIVE("ACTIVE"),
    DELETED("DELETED"),
    HIDDEN("HIDDEN"),
    ARCHIVED("ARCHIVED");

    private final String value;

    Status(String value) {
        this.value = value;
    }

}