package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftConfigPolicyTest {
    @Test
    void acceptsAllWellFormedClientOptionsExceptKeybinds() {
        assertTrue(MinecraftConfigPolicy.isValidClientOption("lang", "ja_jp"));
        assertTrue(MinecraftConfigPolicy.isValidClientOption("graphicsMode", "1"));
        assertTrue(MinecraftConfigPolicy.isValidClientOption("lastServer", "example.invalid:25565"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption("key_key.forward", "key.keyboard.w"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption("invalid:key", "value"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption("invalid\nkey", "value"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption("lang", "ja_jp\nmalicious:true"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption(null, "value"));
        assertFalse(MinecraftConfigPolicy.isValidClientOption("lang", null));
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
