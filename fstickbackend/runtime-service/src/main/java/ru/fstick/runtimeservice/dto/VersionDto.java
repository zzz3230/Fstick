package ru.fstick.runtimeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionDto {
    private UUID versionId;   // deserializes from JSON "version_id" (SNAKE_CASE config)
    private String version;
    private String runtime;
}

