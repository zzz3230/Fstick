package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstallWarning {
    private String code;
    private String message;
}