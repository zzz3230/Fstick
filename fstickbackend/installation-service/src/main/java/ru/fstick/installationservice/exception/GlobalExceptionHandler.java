package ru.fstick.installationservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getCode(), "message", ex.getMessage()));
    }

    @ExceptionHandler(PluginAlreadyInstalledException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleAlreadyInstalled(PluginAlreadyInstalledException ex) {
        return Map.of(
                "error", "already_installed",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(InstallationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(InstallationNotFoundException ex) {
        return Map.of(
                "error", "installation_not_found",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(ForbiddenException ex) {
        return Map.of(
                "error", "forbidden",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneral(Exception ex) {
        return Map.of(
                "error", "internal_error",
                "message", String.valueOf(ex.getMessage())
        );
    }

    @ExceptionHandler(PluginNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handlePluginNotFound(PluginNotFoundException ex) {
        return Map.of("error", "plugin_not_found", "message", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("error", "invalid_token", "message", ex.getMessage());
    }
}
