package ru.fstick.registry_service.dto.api.request.commit;


import lombok.Data;

import java.util.List;

@Data
public class CommitAssetsRequest {
    private List<String> keys;
}
