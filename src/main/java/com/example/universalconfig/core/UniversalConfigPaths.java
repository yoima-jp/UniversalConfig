package com.example.universalconfig.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class UniversalConfigPaths {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SETTINGS_FILE_NAME = "settings.json";

    private UniversalConfigPaths() {
    }

    public static Path defaultRootDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, ".universal-config");
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".universal-config");
    }

    public static UniversalConfigSettings loadOrCreateSettings(Path minecraftRunDirectory) throws UniversalConfigException {
        Path localSettings = minecraftRunDirectory.resolve("config").resolve("universal_config_settings.json");
        if (Files.exists(localSettings)) {
            try (Reader reader = Files.newBufferedReader(localSettings, StandardCharsets.UTF_8)) {
                SettingsDto dto = GSON.fromJson(reader, SettingsDto.class);
                if (dto != null && dto.rootDirectory != null && !dto.rootDirectory.isBlank()) {
            UniversalConfigSettings settings = new UniversalConfigSettings(Path.of(dto.rootDirectory));
            ensureDirectories(settings);
            FileOperationLogger.configure(settings);
            return settings;
                }
            } catch (IOException | RuntimeException ex) {
                throw new UniversalConfigException("Failed to read Universal Config settings.", ex);
            }
        }

        UniversalConfigSettings settings = new UniversalConfigSettings(defaultRootDirectory());
        ensureDirectories(settings);
        FileOperationLogger.configure(settings);
        saveSettings(minecraftRunDirectory, settings);
        return settings;
    }

    public static void saveSettings(Path minecraftRunDirectory, UniversalConfigSettings settings) throws UniversalConfigException {
        Path localSettings = minecraftRunDirectory.resolve("config").resolve("universal_config_settings.json");
        try {
            Files.createDirectories(localSettings.getParent());
            SettingsDto dto = new SettingsDto();
            dto.rootDirectory = settings.rootDirectory().toAbsolutePath().normalize().toString();
            try (Writer writer = Files.newBufferedWriter(localSettings, StandardCharsets.UTF_8)) {
                GSON.toJson(dto, writer);
            }
            FileOperationLogger.info("WRITE_SETTINGS", localSettings, "saved local settings");
            ensureDirectories(settings);
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to save Universal Config settings.", ex);
        }
    }

    public static void ensureDirectories(UniversalConfigSettings settings) throws UniversalConfigException {
        try {
            Files.createDirectories(settings.rootDirectory());
            FileOperationLogger.info("CREATE_DIRECTORY", settings.rootDirectory(), "ensure root directory");
            Files.createDirectories(profilesDirectory(settings));
            FileOperationLogger.info("CREATE_DIRECTORY", profilesDirectory(settings), "ensure profiles directory");
            Files.createDirectories(backupsDirectory(settings));
            FileOperationLogger.info("CREATE_DIRECTORY", backupsDirectory(settings), "ensure backups directory");
            Files.createDirectories(settings.rootDirectory().resolve("logs"));
            FileOperationLogger.info("CREATE_DIRECTORY", settings.rootDirectory().resolve("logs"), "ensure logs directory");
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to create Universal Config directories.", ex);
        }
    }

    public static Path profilesDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve("profiles");
    }

    public static Path backupsDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve("backups");
    }

    public static Path rootSettingsFile(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(SETTINGS_FILE_NAME);
    }

    public static String safeFileSlug(String value) {
        String lower = value == null ? "profile" : value.toLowerCase(Locale.ROOT).trim();
        String slug = lower.replaceAll("[^a-z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? "profile" : slug;
    }

    private static final class SettingsDto {
        String rootDirectory;
    }
}
