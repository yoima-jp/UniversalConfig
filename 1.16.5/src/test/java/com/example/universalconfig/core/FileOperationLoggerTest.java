package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileOperationLoggerTest {
    @TempDir
    Path tempDir;

    @Test
    void removesKnownAndUnknownAbsolutePaths() {
        Path instance = tempDir.resolve("instance");
        Path shared = tempDir.resolve("shared");
        FileOperationLogger.configure(new UniversalConfigSettings(shared), instance);

        String known = FileOperationLogger.sanitizeText(
                "source=" + instance.resolve("options.txt") + " target=" + shared.resolve("profiles/test.ucp"));
        assertTrue(known.contains("<minecraft-instance>/options.txt"));
        assertTrue(known.contains("<universal-config>/profiles/test.ucp"));
        assertFalse(known.contains(tempDir.toString()));

        String unknown = FileOperationLogger.sanitizeText("source=/tmp/private/data.txt reference=https://example.com/path");
        assertTrue(unknown.contains("source=<absolute-path>"));
        assertTrue(unknown.contains("https://example.com/path"));
        assertFalse(unknown.contains("/tmp/private/data.txt"));
    }
}
