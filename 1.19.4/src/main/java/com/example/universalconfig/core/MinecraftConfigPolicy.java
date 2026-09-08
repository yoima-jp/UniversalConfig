package com.example.universalconfig.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Editable policy for files and options that can be shared between instances.
 * Keep Minecraft-version-specific additions in this class instead of adapter logic.
 */
public final class MinecraftConfigPolicy {
    private static final Set<String> DENIED_CONFIG_TOP_LEVEL_DIRECTORIES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "mods", "saves", "logs", "crash-reports", "resourcepacks", "shaderpacks", "screenshots"
    )));

    private static final Set<String> CONFIG_FILE_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            ".cfg", ".json", ".toml", ".yaml", ".yml", ".properties", ".conf", ".txt"
    )));

    private MinecraftConfigPolicy() {
    }

    public static boolean isValidClientOption(String key, String value) {
        if (key == null || key.trim().isEmpty() || value == null) {
            return false;
        }
        // Key bindings have a separate compatibility representation. Accepting key_ here would
        // bypass its legacy/modern value conversion and the user's keybind inclusion choice.
        return !key.startsWith("key_")
                && key.indexOf(':') < 0
                && !containsControlCharacter(key)
                && !containsControlCharacter(value);
    }

    public static boolean isAllowedConfigFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return false;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return CONFIG_FILE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isDeniedConfigTopLevel(String directoryName) {
        return directoryName != null
                && DENIED_CONFIG_TOP_LEVEL_DIRECTORIES.contains(directoryName.toLowerCase(Locale.ROOT));
    }

    private static boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
