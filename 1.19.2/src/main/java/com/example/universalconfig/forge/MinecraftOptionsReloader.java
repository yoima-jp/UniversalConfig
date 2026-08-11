package com.example.universalconfig.forge;

import com.example.universalconfig.core.FileOperationLogger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Synchronizes the already-running client with an options.txt replaced by a profile.
 */
public final class MinecraftOptionsReloader {
    private MinecraftOptionsReloader() {
    }

    public static void reloadFromDisk(Path optionsPath) {
        Minecraft minecraft = Minecraft.getInstance();
        LanguageManager languageManager = minecraft.getLanguageManager();
        LanguageInfo previouslyLoadedLanguage = languageManager.getSelected();

        minecraft.options.load();
        KeyMapping.resetMapping();

        String configuredLanguageCode = minecraft.options.languageCode;
        LanguageInfo configuredLanguage = configuredLanguageCode == null
                ? null : languageManager.getLanguage(configuredLanguageCode);
        boolean reloadLanguageResources = configuredLanguage != null
                && !Objects.equals(previouslyLoadedLanguage, configuredLanguage);
        if (reloadLanguageResources) {
            // options.load() only changes the persisted language code. The live LanguageManager still points at the
            // language selected before Forge applied the first-start profile, so translations require a resource reload.
            languageManager.setSelected(configuredLanguage);
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                if (failure == null) {
                    FileOperationLogger.info("RELOAD_LANGUAGE_RESOURCES", optionsPath,
                            "language=" + configuredLanguageCode);
                } else {
                    FileOperationLogger.failure("RELOAD_LANGUAGE_RESOURCES", optionsPath,
                            "language=" + configuredLanguageCode, failure);
                }
            });
        }

        minecraft.options.save();
        FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", optionsPath,
                "load/resetMapping/save languageReload=" + reloadLanguageResources);
    }
}
