package ru.fstick.installationservice.exception;

import java.util.UUID;

public class InstallationNotFoundException extends RuntimeException {
    public InstallationNotFoundException(UUID installationId) {
        super("Installation with id " + installationId + " not found");
    }
}