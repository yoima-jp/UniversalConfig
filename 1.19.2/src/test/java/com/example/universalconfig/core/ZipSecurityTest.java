package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipSecurityTest {
    @Test
    void rejectsTraversalAbsoluteAndUnsafeSizes() {
        assertThrows(UniversalConfigException.class,
                () -> ZipSecurity.validateRelativeEntryName("../options.txt"));
        assertThrows(UniversalConfigException.class,
                () -> ZipSecurity.validateRelativeEntryName("C:\\Windows\\system.ini"));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "large.txt", ZipSecurity.MAX_ENTRY_UNCOMPRESSED_BYTES + 1, 1, 0));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "ratio.txt", ZipSecurity.MAX_COMPRESSION_RATIO + 1, 1, 0));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "total.txt", 1, 1, ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES));
    }

    @Test
    void acceptsSafeEntryWithinLimits() throws Exception {
        Path root = Path.of("build", "tmp", "zip-root");
        assertEquals(root.toAbsolutePath().normalize().resolve("profile/config.json"),
                ZipSecurity.safeResolve(root, "profile/config.json"));
        assertEquals(300, ZipSecurity.validateEntrySizes("safe.txt", 300, 3, 0));
    }
}
