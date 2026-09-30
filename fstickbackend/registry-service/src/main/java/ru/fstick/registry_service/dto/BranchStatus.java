package ru.fstick.registry_service.dto;

public enum BranchStatus {
    WORKING,
    WAITING_APPROVE,
    APPROVING,
    RELEASED,
    REJECTED,
    CANCELLED;

    public boolean isCandidate() {
        return this == WAITING_APPROVE || this == APPROVING;
    }
}
