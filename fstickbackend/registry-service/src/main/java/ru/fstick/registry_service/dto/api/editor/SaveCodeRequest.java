package ru.fstick.registry_service.dto.api.editor;

public record SaveCodeRequest(Side client, Side server, Object runtime) {

    public record Side(String text, String baseSha) {}
}
