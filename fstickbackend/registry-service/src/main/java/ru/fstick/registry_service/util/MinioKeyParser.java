package ru.fstick.registry_service.util;
import java.util.regex.Pattern;
public class MinioKeyParser {

    private static final Pattern SCREENSHOT_PATTERN =
            Pattern.compile(".*/screenshots/[^/]+\\.(png|jpg|jpeg)$");

    private static final Pattern ICON_PATTERN =
            Pattern.compile(".*/icon(\\.(png|jpg|jpeg))?$");

    public static KeyType resolveType(String key) {
        if (key == null || key.isBlank()) {
            return KeyType.UNDEFINED;
        }

        if (ICON_PATTERN.matcher(key).matches()) {
            return KeyType.ICON;
        }

        if (SCREENSHOT_PATTERN.matcher(key).matches()) {
            return KeyType.SCREENSHOT;
        }

        return KeyType.UNDEFINED;
    }
}
