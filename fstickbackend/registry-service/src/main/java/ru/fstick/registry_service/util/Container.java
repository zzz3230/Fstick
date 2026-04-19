package ru.fstick.registry_service.util;

import lombok.Getter;

@Getter
public enum Container {
    CODE("code"),
    IMAGE("image");

    private final String value;

    Container(String value) {
        this.value = value;
    }


    public static Container fromValue(String value) {
        for (Container c : values()) {
            if (c.value.equalsIgnoreCase(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown container: " + value);
    }

}
