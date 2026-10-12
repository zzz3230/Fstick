package ru.fstick.registry_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class BlobStore {

    private static final String CONTENT_TYPE = "text/plain; charset=utf-8";

    private final S3Service s3Service;

    public static String hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String key(String pluginId, String hex) {
        return "plugins/" + pluginId + "/blobs/" + hex;
    }

    public String put(String pluginId, String text) {
        String hex = hex(text);
        String key = key(pluginId, hex);
        if (!s3Service.exists(key)) {
            s3Service.putObject(key, text.getBytes(StandardCharsets.UTF_8), CONTENT_TYPE);
        }
        return hex;
    }

    public String get(String pluginId, String hex) {
        String key = key(pluginId, hex);
        try {
            return new String(s3Service.getObject(key), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Blob missing or unreadable: " + key, e);
        }
    }
}
