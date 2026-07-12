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

    private UniversalConfigPaths() {
    }

    public static Path defaultRootDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, UniversalConfigFormat.ROOT_DIRECTORY_NAME);
        }
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, UniversalConfigFormat.ROOT_DIRECTORY_NAME);
    }

    public static UniversalConfigSettings loadOrCreateSettings(Path minecraftRunDirectory) throws UniversalConfigException {
        Path localSettings = localSettingsFile(minecraftRunDirectory);
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
        Path localSettings = localSettingsFile(minecraftRunDirectory);
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
            Files.createDirectories(logsDirectory(settings));
            FileOperationLogger.info("CREATE_DIRECTORY", logsDirectory(settings), "ensure logs directory");
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to create Universal Config directories.", ex);
        }
    }

    public static Path profilesDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(UniversalConfigFormat.PROFILES_DIRECTORY_NAME);
    }

    public static Path backupsDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(UniversalConfigFormat.BACKUPS_DIRECTORY_NAME);
    }

    public static Path rootSettingsFile(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(UniversalConfigFormat.ROOT_SETTINGS_FILE_NAME);
    }

    public static Path configDirectory(Path minecraftRunDirectory) {
        return minecraftRunDirectory.resolve(UniversalConfigFormat.CONFIG_DIRECTORY_NAME);
    }

    public static Path optionsFile(Path minecraftRunDirectory) {
        return minecraftRunDirectory.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
    }

    public static Path localSettingsFile(Path minecraftRunDirectory) {
        return configDirectory(minecraftRunDirectory).resolve(UniversalConfigFormat.LOCAL_SETTINGS_FILE_NAME);
    }

    public static Path pendingImportFile(Path minecraftRunDirectory) {
        return configDirectory(minecraftRunDirectory).resolve(UniversalConfigFormat.PENDING_IMPORT_FILE_NAME);
    }

    public static Path logsDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(UniversalConfigFormat.LOGS_DIRECTORY_NAME);
    }

    public static Path exportDirectory(Path minecraftRunDirectory) {
        return minecraftRunDirectory.resolve(UniversalConfigFormat.EXPORT_DIRECTORY_NAME);
    }

    public static String safeFileSlug(String value) {
        String lower = value == null
                ? UniversalConfigFormat.DEFAULT_PROFILE_SLUG
                : value.toLowerCase(Locale.ROOT).trim();
        String slug = lower.replaceAll("[^a-z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? UniversalConfigFormat.DEFAULT_PROFILE_SLUG : slug;
    }

    private static final class SettingsDto {
        String rootDirectory;
    }
}
