package ru.fstick.installationservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class ResolveResponse {
    private UUID branchId;
    private String branchStatus;
}
