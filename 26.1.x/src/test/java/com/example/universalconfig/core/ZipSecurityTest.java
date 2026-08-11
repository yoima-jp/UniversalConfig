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
}
