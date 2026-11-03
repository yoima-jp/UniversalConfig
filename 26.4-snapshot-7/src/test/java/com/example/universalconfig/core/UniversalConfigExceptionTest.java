package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class UniversalConfigExceptionTest {
    @Test
    void keepsTranslationKeyAndCauseWithoutTreatingCauseAsTranslationArgument() {
        RuntimeException cause = new RuntimeException("cause");

        UniversalConfigException exception = new UniversalConfigException(
                "internal message",
                "message.universal_config.reload_options_failed",
                cause);

        assertEquals("message.universal_config.reload_options_failed", exception.translationKey());
        assertArrayEquals(new Object[0], exception.translationArgs());
        assertSame(cause, exception.getCause());
    }
}
