package ru.fstick.integrationservice.dto.response;

import java.util.UUID;

public record ResolveIdentityResponse(UUID internalUuid, boolean created) {
}
