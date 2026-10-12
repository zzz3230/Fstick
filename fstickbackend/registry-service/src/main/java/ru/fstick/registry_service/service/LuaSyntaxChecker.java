package ru.fstick.registry_service.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.compiler.LuaC;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LuaSyntaxChecker {

    private static final String CHUNK_NAME = "sv.lua";
    private static final Pattern LOCATION =
            Pattern.compile("^(?:\\[string \"[^\"]*\"\\]|[^:\\s]+):(\\d+):\\s*(.*)$", Pattern.DOTALL);

    public Optional<Problem> check(String source) {
        try {
            LuaC.instance.compile(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), CHUNK_NAME);
            return Optional.empty();
        } catch (LuaError error) {
            return Optional.of(toProblem(error.getMessage()));
        } catch (IOException e) {
            return Optional.of(new Problem(String.valueOf(e.getMessage()), null));
        }
    }

    private static Problem toProblem(String message) {
        if (message == null) {
            return new Problem("syntax error", null);
        }
        Matcher matcher = LOCATION.matcher(message);
        if (matcher.matches()) {
            return new Problem(matcher.group(2), Integer.parseInt(matcher.group(1)));
        }
        return new Problem(message, null);
    }

    public record Problem(String message, Integer line) {}
}
