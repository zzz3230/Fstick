package ru.fstick.registry_service.util;

import org.junit.jupiter.api.Test;
import ru.fstick.registry_service.exception.ApiException;

import static org.junit.jupiter.api.Assertions.*;

class ShaFormatTest {

    private static final String HEX = "ab".repeat(32);

    @Test
    void format_addsPrefix() {
        assertEquals("sha256:" + HEX, ShaFormat.format(HEX));
    }

    @Test
    void parse_validSha_returnsHex() {
        assertEquals(HEX, ShaFormat.parse("sha256:" + HEX));
    }

    @Test
    void parse_wrongPrefix_throwsInvalidSha() {
        ApiException ex = assertThrows(ApiException.class, () -> ShaFormat.parse("md5:" + HEX));
        assertEquals("invalid_sha", ex.getCode());
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void parse_uppercaseHex_throwsInvalidSha() {
        assertThrows(ApiException.class, () -> ShaFormat.parse("sha256:" + HEX.toUpperCase()));
    }

    @Test
    void parse_wrongLength_throwsInvalidSha() {
        assertThrows(ApiException.class, () -> ShaFormat.parse("sha256:abcd"));
    }

    @Test
    void parse_null_throwsInvalidSha() {
        assertThrows(ApiException.class, () -> ShaFormat.parse(null));
    }
}
