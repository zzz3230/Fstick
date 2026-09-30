package ru.fstick.registry_service.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaSyntaxCheckerTest {

    private final LuaSyntaxChecker checker = new LuaSyntaxChecker();

    @Test
    void validSource_hasNoProblem() {
        assertTrue(checker.check("local x = 1\nreturn x").isEmpty());
    }

    @Test
    void syntaxError_reportsLine() {
        LuaSyntaxChecker.Problem problem = checker.check("local a = 1\nlocal b = 2\nlocal = = 3").orElseThrow();

        assertEquals(3, problem.line());
        assertEquals("'<name>' expected", problem.message());
        assertNotNull(problem.message());
    }

    @Test
    void codeIsNotExecuted() {
        assertTrue(checker.check("error('boom')").isEmpty());
    }
}
