package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniversalConfigFormatTest {
    @Test
    void buildsStableArchiveEntryNames() {
        assertEquals("profile/config-files/mod/example.json",
                UniversalConfigFormat.profileConfigEntry("mod\\example.json"));
        assertTrue(UniversalConfigFormat.isProfileConfigEntry("profile/config-files/mod/example.json"));
        assertEquals("mod/example.json",
                UniversalConfigFormat.profileConfigRelativePath("profile/config-files/mod/example.json"));
        assertEquals("original/config/mod/example.json",
                UniversalConfigFormat.backupConfigEntry("mod/example.json"));
    }
}
