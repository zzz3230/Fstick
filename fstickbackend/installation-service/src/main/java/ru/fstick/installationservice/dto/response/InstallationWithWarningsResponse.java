package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class InstallationWithWarningsResponse {
    private InstallationResponse installation;
    private List<InstallWarning> warnings;
}