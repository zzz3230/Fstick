package ru.fstick.registry_service.util;

import org.springframework.http.HttpStatus;
import ru.fstick.registry_service.exception.ApiException;

import java.util.regex.Pattern;

public final class ShaFormat {

    private static final String PREFIX = "sha256:";
    private static final Pattern HEX = Pattern.compile("^[0-9a-f]{64}$");

    private ShaFormat() {
    }

    public static String format(String hex) {
        return PREFIX + hex;
    }

    public static String parse(String sha) {
        if (sha == null || !sha.startsWith(PREFIX)) {
            throw invalid(sha);
        }
        String hex = sha.substring(PREFIX.length());
        if (!HEX.matcher(hex).matches()) {
            throw invalid(sha);
        }
        return hex;
    }

    private static ApiException invalid(String sha) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_sha", "Invalid sha: " + sha);
    }
}
