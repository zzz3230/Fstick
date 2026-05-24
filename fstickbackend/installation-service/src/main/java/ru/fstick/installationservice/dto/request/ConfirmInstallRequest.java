package ru.fstick.installationservice.dto.request;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmInstallRequest {
    private String confirmationToken;
}