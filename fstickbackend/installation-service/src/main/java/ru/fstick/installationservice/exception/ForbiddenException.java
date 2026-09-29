package ru.fstick.installationservice.exception;

import java.util.UUID;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(UUID userId, String chatId) {
        super("User " + userId + " is not a member of chat " + chatId);
    }
}