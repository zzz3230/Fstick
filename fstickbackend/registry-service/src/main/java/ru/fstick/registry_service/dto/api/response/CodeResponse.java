package ru.fstick.registry_service.dto.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CodeResponse {
    private String text;
    private String sha;
}
