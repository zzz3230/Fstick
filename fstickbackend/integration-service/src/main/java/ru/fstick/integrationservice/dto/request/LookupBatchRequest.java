package ru.fstick.integrationservice.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class LookupBatchRequest {
    private List<UUID> uuids;
}
