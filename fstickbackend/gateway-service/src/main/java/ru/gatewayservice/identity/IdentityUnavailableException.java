package ru.gatewayservice.identity;

public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
