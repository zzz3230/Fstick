package ru.fstick.registry_service.dto.api.request.commit;


import lombok.Data;

@Data
public class CommitVersionRequest {
    private String version;
    private String changelog;
}
