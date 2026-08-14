package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationLoggerTest {
    @TempDir
    Path tempDir;

    @Test
    void redactsKnownRootsWhileKeepingRelativeDiagnostics() {
        Path instance = tempDir.resolve("minecraft-instance");
        Path sharedRoot = tempDir.resolve("shared-root");
        FileOperationLogger.configure(new UniversalConfigSettings(sharedRoot), instance);

        assertEquals("<minecraft-instance>/config/options.txt",
                FileOperationLogger.sanitizePath(instance.resolve("config/options.txt")));
        assertEquals("<universal-config>/profiles/test.ucp",
                FileOperationLogger.sanitizePath(sharedRoot.resolve("profiles/test.ucp")));

        String detail = "source=" + instance.resolve("options.txt")
                + " target=" + sharedRoot.resolve("profiles/test.ucp");
        String sanitized = FileOperationLogger.sanitizeText(detail);
        assertTrue(sanitized.contains("<minecraft-instance>/options.txt"));
        assertTrue(sanitized.contains("<universal-config>/profiles/test.ucp"));
        assertFalse(sanitized.contains(instance.toString()));
        assertFalse(sanitized.contains(sharedRoot.toString()));
    }

    @Test
    void redactsUnknownUnixAbsolutePathsFromDiagnostics() {
        String detail = "source=/tmp/universal-config/stacktrace.log"
                + " target=/opt/minecraft/config/options.txt"
                + " reference=https://example.com/path";

        String sanitized = FileOperationLogger.sanitizeText(detail);

        assertFalse(sanitized.contains("/tmp/universal-config/stacktrace.log"));
        assertFalse(sanitized.contains("/opt/minecraft/config/options.txt"));
        assertTrue(sanitized.contains("source=<absolute-path>"));
        assertTrue(sanitized.contains("target=<absolute-path>"));
        assertTrue(sanitized.contains("https://example.com/path"));
    }

    @Test
    void preservesRelativePathsFollowingRedactionPlaceholders() {
        String detail = "source=<minecraft-instance>/options.txt"
                + " target=<universal-config>/profiles/test.ucp";

        assertEquals(detail, FileOperationLogger.sanitizeText(detail));
    }
}
