package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipSecurityTest {
    @Test
    void rejectsParentTraversalEntries() {
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateRelativeEntryName("../options.txt"));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateRelativeEntryName("profile/../../evil.txt"));
    }

    @Test
    void rejectsAbsoluteEntries() {
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateRelativeEntryName("C:\\Windows\\System32\\drivers\\etc\\hosts"));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateRelativeEntryName("C:relative-path.txt"));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateRelativeEntryName("/etc/passwd"));
    }

    @Test
    void resolvesInsideDestination() throws UniversalConfigException {
        Path root = Path.of("build", "tmp", "zip-root");
        Path resolved = ZipSecurity.safeResolve(root, "profile/config.json");
        assertEquals(root.toAbsolutePath().normalize().resolve("profile/config.json").normalize(), resolved);
    }

    @Test
    void rejectsUnsafeEntrySizes() {
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "large.txt", ZipSecurity.MAX_ENTRY_UNCOMPRESSED_BYTES + 1, 1, 0));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "ratio.txt", ZipSecurity.MAX_COMPRESSION_RATIO + 1, 1, 0));
        assertThrows(UniversalConfigException.class, () -> ZipSecurity.validateEntrySizes(
                "total.txt", 1, 1, ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES));
    }

    @Test
    void acceptsEntriesWithinAllLimits() throws UniversalConfigException {
        long total = ZipSecurity.validateEntrySizes("first.txt", 100, 10, 0);
        total = ZipSecurity.validateEntrySizes("second.txt", 200, 20, total);
        assertEquals(300, total);
    }
}
