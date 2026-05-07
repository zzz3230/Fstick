package ru.fstick.installationservice.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String userId, String chatId) {
        super("User " + userId + " is not a member of chat " + chatId);
    }
}