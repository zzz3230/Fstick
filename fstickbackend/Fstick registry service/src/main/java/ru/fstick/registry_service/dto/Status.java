package ru.fstick.registry_service.dto;

import lombok.Getter;

@Getter
public enum Status {
    DELETED("DELETED");

    private final String value;

    Status(String value) {
        this.value = value;
    }

}