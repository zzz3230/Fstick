package ru.fstick.registry_service.service;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class TemplateProvider {

    public static final String ICON_KEY = "templates/icon.png";

    private static final Logger log = LoggerFactory.getLogger(TemplateProvider.class);

    private final S3Service s3Service;

    @Getter
    private final String clientCode;
    @Getter
    private final String serverCode;
    private final byte[] icon;

    public TemplateProvider(S3Service s3Service) {
        this.s3Service = s3Service;
        this.clientCode = new String(read("client.js"), StandardCharsets.UTF_8);
        this.serverCode = new String(read("server.lua"), StandardCharsets.UTF_8);
        this.icon = read("icon.png");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void uploadIcon() {
        try {
            if (!s3Service.exists(ICON_KEY)) {
                s3Service.putObject(ICON_KEY, icon, "image/png");
            }
        } catch (RuntimeException e) {
            log.error("Failed to upload template icon", e);
        }
    }

    private static byte[] read(String name) {
        try {
            return new ClassPathResource("templates/" + name).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
