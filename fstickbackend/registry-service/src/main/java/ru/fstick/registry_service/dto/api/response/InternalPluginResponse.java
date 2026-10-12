package ru.fstick.registry_service.dto.api.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InternalPluginResponse {
    private UUID id;
    private String status;
    private UUID authorId;
    private List<InternalBranch> branches;

    @Data
    @Builder
    public static class InternalBranch {
        private UUID id;
        private String status;
        private String semver;
        private String serverBlobSha;
        private String clientBlobSha;
    }
}
