package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class InstallationPendingResponse {
    private String confirmationToken;
    private List<InstallWarning> warnings;
}