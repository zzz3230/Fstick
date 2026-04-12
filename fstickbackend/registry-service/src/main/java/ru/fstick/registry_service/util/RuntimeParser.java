package ru.fstick.registry_service.util;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuntimeParser {

    private static final Pattern PATTERN =
            Pattern.compile("^(sv|cl)\\.([a-zA-Z0-9_-]+)@(\\d+\\.\\d+\\.\\d+)$");

    @Data
    public static class Result {
        public String target;
        public String language;
        public String version;
    }

    public static Result parse(String input) {
        Matcher matcher = PATTERN.matcher(input);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid runtime format: " + input);
        }

        Result r = new Result();
        r.target = matcher.group(1);
        r.language = matcher.group(2);
        r.version = matcher.group(3);

        return r;
    }
}