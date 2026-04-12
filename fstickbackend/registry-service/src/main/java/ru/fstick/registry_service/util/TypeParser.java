package ru.fstick.registry_service.util;

import lombok.Data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeParser {

    private static final Pattern PATTERN =
            Pattern.compile("^(code|image)/([a-zA-Z0-9_:.@-]+)$");

    @Data
    public static class Result {
        public Container container;
        public String type;
    }

    public static Result parse(String input) {
        Matcher matcher = PATTERN.matcher(input);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid type format: " + input);
        }

        Result r = new Result();
        r.container = Container.fromValue(matcher.group(1));
        r.type = matcher.group(2);

        return r;
    }
}