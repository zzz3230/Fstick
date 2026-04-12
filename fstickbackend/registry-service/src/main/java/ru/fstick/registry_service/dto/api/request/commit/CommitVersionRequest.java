package ru.fstick.registry_service.dto.api.request.commit;


import lombok.Data;

import java.util.List;

@Data
public class CommitVersionRequest {
    private String version;
    private String changelog;
    private List<String> keys;
}
