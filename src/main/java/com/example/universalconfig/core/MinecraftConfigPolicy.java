package com.example.universalconfig.core;

import java.util.Locale;
import java.util.Set;

/**
 * Editable policy for files and options that can be shared between instances.
 * Keep Minecraft-version-specific additions in this class instead of adapter logic.
 */
public final class MinecraftConfigPolicy {
    private static final Set<String> DENIED_CONFIG_TOP_LEVEL_DIRECTORIES = Set.of(
            "mods", "saves", "logs", "crash-reports", "resourcepacks", "shaderpacks", "screenshots"
    );

    private static final Set<String> CLIENT_OPTION_KEYS = Set.of(
            "lang", "gamma", "fov", "guiScale", "soundDevice", "autoJump",
            "operatorItemsTab", "touchscreen", "fullscreen", "bobView",
            "darkMojangStudiosBackground", "hideLightningFlashes", "hideSplashTexts",
            "panoramaScrollSpeed", "pauseOnLostFocus", "enableVsync", "entityShadows",
            "forceUnicodeFont", "discrete_mouse_scroll", "mouseSensitivity", "invertYMouse",
            "rawMouseInput", "reducedDebugInfo", "showSubtitles", "directionalAudio",
            "narrator", "tutorialStep"
    );

    private static final Set<String> CONFIG_FILE_EXTENSIONS = Set.of(
            ".cfg", ".json", ".toml", ".yaml", ".yml", ".properties", ".conf", ".txt"
    );

    private MinecraftConfigPolicy() {
    }

    public static boolean isAllowedClientOption(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return CLIENT_OPTION_KEYS.contains(key)
                || key.startsWith("soundCategory_")
                || key.startsWith("modelPart_");
    }

    public static boolean isAllowedConfigFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        return CONFIG_FILE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isDeniedConfigTopLevel(String directoryName) {
        return directoryName != null
                && DENIED_CONFIG_TOP_LEVEL_DIRECTORIES.contains(directoryName.toLowerCase(Locale.ROOT));
    }
}
