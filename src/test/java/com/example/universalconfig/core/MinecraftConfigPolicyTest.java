package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftConfigPolicyTest {
    @Test
    void allowsKnownClientOptionsAndOptionPrefixes() {
        assertTrue(MinecraftConfigPolicy.isAllowedClientOption("lang"));
        assertTrue(MinecraftConfigPolicy.isAllowedClientOption("soundCategory_music"));
        assertTrue(MinecraftConfigPolicy.isAllowedClientOption("modelPart_hat"));
        assertFalse(MinecraftConfigPolicy.isAllowedClientOption("lastServer"));
    }

    @Test
    void limitsConfigFilesAndProtectedDirectories() {
        assertTrue(MinecraftConfigPolicy.isAllowedConfigFile("mod/example.json"));
        assertFalse(MinecraftConfigPolicy.isAllowedConfigFile("mod/example.jar"));
        assertFalse(MinecraftConfigPolicy.isAllowedConfigFile(null));
        assertTrue(MinecraftConfigPolicy.isDeniedConfigTopLevel("SAVES"));
        assertFalse(MinecraftConfigPolicy.isDeniedConfigTopLevel("example"));
    }
}
