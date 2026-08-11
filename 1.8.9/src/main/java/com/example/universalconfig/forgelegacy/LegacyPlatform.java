package com.example.universalconfig.forgelegacy;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.MinecraftEnvironment;
import com.example.universalconfig.core.ModLoader;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.ForgeVersion;

import java.nio.file.Path;
import java.util.Objects;

final class LegacyPlatform {
    private LegacyPlatform() {
    }

    static Path gameDirectory() {
        return Minecraft.getMinecraft().mcDataDir.toPath().toAbsolutePath().normalize();
    }

    static ProfileService service() throws UniversalConfigException {
        UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory());
        return new ProfileService(settings);
    }

    static MinecraftEnvironment environment() {
        return environment(gameDirectory());
    }

    static MinecraftEnvironment environment(Path gameDirectory) {
        return new MinecraftEnvironment(gameDirectory.toAbsolutePath().normalize(), LegacyVersionBridge.minecraftVersion(),
                ModLoader.FORGE, ForgeVersion.getVersion());
    }

    static void reloadOptions() throws UniversalConfigException {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            LanguageManager languageManager = minecraft.getLanguageManager();
            Language previouslyLoadedLanguage = languageManager.getCurrentLanguage();
            minecraft.gameSettings.loadOptions();
            KeyBinding.resetKeyBindingArrayAndHash();
            String configuredLanguageCode = minecraft.gameSettings.language;
            Language configuredLanguage = findLanguage(languageManager, configuredLanguageCode);
            boolean reloadLanguageResources = configuredLanguage != null
                    && !Objects.equals(previouslyLoadedLanguage, configuredLanguage);
            if (configuredLanguage != null) {
                // 1.7.10/1.8.9 use mixed-case locale IDs (for example ja_JP), while profiles created by newer
                // Minecraft versions contain lowercase IDs. Persist the metadata's canonical ID as well as selecting
                // it, otherwise the next process starts with an unknown locale and silently falls back to en_US.
                minecraft.gameSettings.language = configuredLanguage.getLanguageCode();
                languageManager.setCurrentLanguage(configuredLanguage);
            }
            if (reloadLanguageResources) {
                // GameSettings.loadOptions() changes only the stored value on these versions. Refresh after selecting
                // the matching Language object so a first-start profile changes the live UI in the same process.
                minecraft.refreshResources();
                FileOperationLogger.info("RELOAD_LANGUAGE_RESOURCES",
                        UniversalConfigPaths.optionsFile(gameDirectory()),
                        "language=" + configuredLanguage.getLanguageCode() + " source=" + configuredLanguageCode);
            }
            minecraft.gameSettings.saveOptions();
            FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(gameDirectory()),
                    "load/resetKeyBindingArrayAndHash/save languageReload=" + reloadLanguageResources);
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(gameDirectory()),
                    "failed", ex);
            throw new UniversalConfigException("Minecraft options could not be reloaded.", ex);
        }
    }

    private static Language findLanguage(LanguageManager languageManager, String languageCode) {
        if (languageCode == null) {
            return null;
        }
        String normalizedLanguageCode = languageCode.replace('-', '_');
        // 1.7.10 and 1.8.9 expose only getLanguages(), while 1.12.2 also has getLanguage(String).
        // Iterating the tiny metadata set keeps the shared legacy source binary-compatible with all three generations.
        for (Object candidate : languageManager.getLanguages()) {
            if (candidate instanceof Language) {
                Language language = (Language) candidate;
                if (normalizedLanguageCode.equalsIgnoreCase(language.getLanguageCode())) {
                    return language;
                }
            }
        }
        return null;
    }

    static void scheduleRestart() throws UniversalConfigException {
        CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(gameDirectory());
    }
}
