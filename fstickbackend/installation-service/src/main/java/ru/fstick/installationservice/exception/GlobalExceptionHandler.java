package ru.fstick.installationservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PluginAlreadyInstalledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyInstalled(PluginAlreadyInstalledException ex) {
        return Map.of(
                "error", "ALREADY_INSTALLED",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(InstallationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(InstallationNotFoundException ex) {
        return Map.of(
                "error", "INSTALLATION_NOT_FOUND",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(ForbiddenException ex) {
        return Map.of(
                "error", "FORBIDDEN",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneral(Exception ex) {
        return Map.of(
                "error", "INTERNAL_ERROR",
                "message", ex.getMessage()
        );
    }
}