package ru.fstick.registry_service.util;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
public class MinioKeyParser {

    private static final Pattern SCREENSHOT_PATTERN =
            Pattern.compile(".*/screenshots/[^/]+\\.(png|jpg|jpeg)$");

    private static final Pattern ICON_PATTERN =
            Pattern.compile(".*/icon\\.png$");

    private static final Pattern FILE_PATTERN =
            Pattern.compile(".*/versions/[^/]+/.+");
    public static KeyType resolveType(String key) {
        if (key == null || key.isBlank()) {
            return KeyType.FILE;
        }

        if (ICON_PATTERN.matcher(key).matches()) {
            return KeyType.ICON;
        }

        if (SCREENSHOT_PATTERN.matcher(key).matches()) {
            return KeyType.SCREENSHOT;
        }

        if(FILE_PATTERN.matcher(key).matches()) {
            return KeyType.FILE;
        }

        return KeyType.UNDEFINED;
    }

    public static List<String> getAllFiles(List<String> keys) {
        List<String> files = new ArrayList<>();

        keys.forEach(key -> {
            if (resolveType(key) == KeyType.FILE) {
                files.add(key);
            }
        });

        return files;
    }

    public static List<String> getAllIcons(List<String> keys) {
        List<String> icons = new ArrayList<>();

        keys.forEach(key -> {
            if (resolveType(key) == KeyType.ICON) {
                icons.add(key);
            }
        });

        return icons;
    }

    public static List<String> getAllScreenshots(List<String> keys) {
        List<String> screenshots = new ArrayList<>();

        keys.forEach(key -> {
            if (resolveType(key) == KeyType.SCREENSHOT) {
                screenshots.add(key);
            }
        });

        return screenshots;
    }
}
