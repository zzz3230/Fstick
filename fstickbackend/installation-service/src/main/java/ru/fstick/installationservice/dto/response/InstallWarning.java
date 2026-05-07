package ru.fstick.installationservice.dto.response;

public class InstallWarning {

    private String code;
    private String message;

    public InstallWarning(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}