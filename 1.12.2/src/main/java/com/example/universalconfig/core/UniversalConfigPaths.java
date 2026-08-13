package com.example.universalconfig.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.List;

public final class UniversalConfigPaths {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UniversalConfigPaths() {
    }

    public static Path defaultRootDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.trim().isEmpty()) {
            return Paths.get(appData, UniversalConfigFormat.ROOT_DIRECTORY_NAME);
        }
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, UniversalConfigFormat.ROOT_DIRECTORY_NAME);
    }

    public static UniversalConfigSettings loadOrCreateSettings(Path minecraftRunDirectory) throws UniversalConfigException {
        Path localSettings = localSettingsFile(minecraftRunDirectory);
        if (Files.exists(localSettings)) {
            try {
                SettingsDto dto;
                try (Reader reader = Files.newBufferedReader(localSettings, StandardCharsets.UTF_8)) {
                    dto = GSON.fromJson(reader, SettingsDto.class);
                }
                if (dto != null && dto.rootDirectory != null && !dto.rootDirectory.trim().isEmpty()) {
                    UniversalConfigSettings settings = new UniversalConfigSettings(Paths.get(dto.rootDirectory));
                    boolean invalidDefaultProfilePath = hasInvalidDefaultProfilePath(dto.defaultProfilePath);
                    settings.setDefaultProfilePath(parseDefaultProfilePath(dto.defaultProfilePath));
                    settings.setProfileOrder(dto.profileOrder);
                    ensureDirectories(settings);
                    FileOperationLogger.configure(settings, minecraftRunDirectory);
                    Path sharedSettingsFile = rootSettingsFile(settings);
                    if (Files.isRegularFile(sharedSettingsFile)) {
                        SettingsDto sharedDto = readSettingsDto(sharedSettingsFile);
                        invalidDefaultProfilePath = hasInvalidDefaultProfilePath(sharedDto == null ? null : sharedDto.defaultProfilePath);
                        settings.setDefaultProfilePath(parseDefaultProfilePath(
                                sharedDto == null ? null : sharedDto.defaultProfilePath));
                        settings.setProfileOrder(sharedDto == null ? null : sharedDto.profileOrder);
                    } else {
                        // Older versions stored the default only inside one instance. Migrate it to the shared root.
                        saveRootSettings(settings);
                    }
                    if (invalidDefaultProfilePath) {
                        settings.setDefaultProfilePath(null);
                        saveSettings(minecraftRunDirectory, settings);
                    }
                    return settings;
                }
            } catch (IOException | RuntimeException ex) {
                throw new UniversalConfigException("Failed to read Universal Config settings.", ex);
            }
        }

        UniversalConfigSettings settings = new UniversalConfigSettings(defaultRootDirectory());
        ensureDirectories(settings);
        FileOperationLogger.configure(settings, minecraftRunDirectory);
        Path sharedSettingsFile = rootSettingsFile(settings);
        if (Files.isRegularFile(sharedSettingsFile)) {
            SettingsDto sharedDto = readSettingsDto(sharedSettingsFile);
            settings.setDefaultProfilePath(parseDefaultProfilePath(
                    sharedDto == null ? null : sharedDto.defaultProfilePath));
            settings.setProfileOrder(sharedDto == null ? null : sharedDto.profileOrder);
        }
        saveSettings(minecraftRunDirectory, settings);
        return settings;
    }

    public static void saveSettings(Path minecraftRunDirectory, UniversalConfigSettings settings) throws UniversalConfigException {
        Path localSettings = localSettingsFile(minecraftRunDirectory);
        try {
            Files.createDirectories(localSettings.getParent());
            SettingsDto dto = new SettingsDto();
            dto.rootDirectory = settings.rootDirectory().toAbsolutePath().normalize().toString();
            dto.defaultProfilePath = settings.defaultProfilePath() == null
                    ? null
                    : settings.defaultProfilePath().toAbsolutePath().normalize().toString();
            dto.profileOrder = settings.profileOrder();
            try (Writer writer = Files.newBufferedWriter(localSettings, StandardCharsets.UTF_8)) {
                GSON.toJson(dto, writer);
            }
            saveRootSettings(settings);
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

    public static Path defaultProfileAppliedMarker(Path minecraftRunDirectory) {
        return configDirectory(minecraftRunDirectory)
                .resolve(UniversalConfigFormat.INTERNAL_DIRECTORY_PREFIX)
                .resolve(UniversalConfigFormat.DEFAULT_PROFILE_APPLIED_MARKER_NAME);
    }

    public static Path logsDirectory(UniversalConfigSettings settings) {
        return settings.rootDirectory().resolve(UniversalConfigFormat.LOGS_DIRECTORY_NAME);
    }

    public static String safeFileSlug(String value) {
        String lower = value == null
                ? UniversalConfigFormat.DEFAULT_PROFILE_SLUG
                : value.toLowerCase(Locale.ROOT).trim();
        String slug = lower.replaceAll("[^a-z0-9._-]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.trim().isEmpty() ? UniversalConfigFormat.DEFAULT_PROFILE_SLUG : slug;
    }

    private static final class SettingsDto {
        String rootDirectory;
        String defaultProfilePath;
        List<String> profileOrder;
    }

    private static SettingsDto readSettingsDto(Path path) throws UniversalConfigException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, SettingsDto.class);
        } catch (IOException | RuntimeException ex) {
            throw new UniversalConfigException("Failed to read Universal Config settings.", ex);
        }
    }

    private static void saveRootSettings(UniversalConfigSettings settings) throws UniversalConfigException {
        Path rootSettings = rootSettingsFile(settings);
        try {
            Files.createDirectories(rootSettings.getParent());
            SettingsDto dto = new SettingsDto();
            dto.rootDirectory = settings.rootDirectory().toAbsolutePath().normalize().toString();
            dto.defaultProfilePath = settings.defaultProfilePath() == null
                    ? null
                    : settings.defaultProfilePath().toAbsolutePath().normalize().toString();
            dto.profileOrder = settings.profileOrder();
            try (Writer writer = Files.newBufferedWriter(rootSettings, StandardCharsets.UTF_8)) {
                GSON.toJson(dto, writer);
            }
            FileOperationLogger.info("WRITE_ROOT_SETTINGS", rootSettings, "saved shared settings");
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to save shared Universal Config settings.", ex);
        }
    }

    private static Path parseDefaultProfilePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Paths.get(value);
        } catch (InvalidPathException ex) {
            return null;
        }
    }

    private static boolean hasInvalidDefaultProfilePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            Paths.get(value);
            return false;
        } catch (InvalidPathException ex) {
            return true;
        }
    }
}
