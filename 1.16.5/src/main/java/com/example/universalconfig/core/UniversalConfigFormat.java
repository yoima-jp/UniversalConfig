package com.example.universalconfig.core;

/**
 * Names shared by the on-disk format and the profile archive.
 * Keep changes here deliberate because these values are part of the file format contract.
 */
public final class UniversalConfigFormat {
    public static final String MOD_ID = "universal_config";
    public static final String ROOT_DIRECTORY_NAME = ".universal-config";
    public static final String DEFAULT_PROFILE_SLUG = "profile";

    public static final int FORMAT_VERSION = 1;
    public static final String PROFILE_FORMAT = "universal-config-profile";
    public static final String BACKUP_FORMAT = "universal-config-backup";
    public static final String PENDING_IMPORT_FORMAT = "universal-config-pending-import";

    public static final String CONFIG_DIRECTORY_NAME = "config";
    public static final String OPTIONS_FILE_NAME = "options.txt";
    public static final String LOCAL_SETTINGS_FILE_NAME = "universal_config_settings.json";
    public static final String ROOT_SETTINGS_FILE_NAME = "settings.json";
    public static final String PENDING_IMPORT_FILE_NAME = "universal_config_pending_import.json";
    public static final String DEFAULT_PROFILE_APPLIED_MARKER_NAME = "default-profile-applied";
    public static final String RESTART_HELPER_LOG_NAME = "restart-helper.log";
    public static final String RESTART_HELPER_DIRECTORY_NAME = "restart";
    public static final String RESTART_READY_FILE_EXTENSION = ".ready";
    public static final String PROFILES_DIRECTORY_NAME = "profiles";
    public static final String PROFILE_OPERATION_LOCK_FILE_NAME = ".profile-operation.lock";
    public static final String BACKUPS_DIRECTORY_NAME = "backups";
    public static final String LOGS_DIRECTORY_NAME = "logs";
    public static final String LAUNCH_LOGS_DIRECTORY_NAME = "launches";
    public static final String LATEST_LOG_FILE_NAME = "latest.log";
    public static final String LAUNCH_LOG_FILE_PREFIX = "universal-config-";

    public static final String PROFILE_FILE_EXTENSION = ".ucp";
    public static final String BACKUP_FILE_EXTENSION = ".ucbackup";

    public static final String MANIFEST_ENTRY = "manifest.json";
    public static final String CHECKSUMS_ENTRY = "checksums.json";
    public static final String PROFILE_README_ENTRY = "README.txt";
    public static final String PROFILE_DIRECTORY = "profile/";
    public static final String PROFILE_CONFIG_DIRECTORY = PROFILE_DIRECTORY + "config-files/";
    public static final String PROFILE_KEYBINDS_ENTRY = PROFILE_DIRECTORY + "keybinds.json";
    public static final String PROFILE_OPTIONS_ENTRY = PROFILE_DIRECTORY + "options-fragments.json";
    public static final String BACKUP_MANIFEST_ENTRY = "backup-manifest.json";
    public static final String BACKUP_ORIGINAL_DIRECTORY = "original/";
    public static final String BACKUP_CONFIG_DIRECTORY = BACKUP_ORIGINAL_DIRECTORY + "config/";
    public static final String BACKUP_FILENAME_PREFIX = "backup-";
    public static final String LEGACY_INTERNAL_DIRECTORY_PREFIX = "universal-config/";
    public static final String INTERNAL_DIRECTORY_PREFIX = "universal_config/";

    private UniversalConfigFormat() {
    }

    public static String profileConfigEntry(String relativePath) {
        return PROFILE_CONFIG_DIRECTORY + normalizeArchivePath(relativePath);
    }

    public static boolean isProfileConfigEntry(String entryName) {
        return entryName != null && entryName.startsWith(PROFILE_CONFIG_DIRECTORY);
    }

    public static String profileConfigRelativePath(String entryName) {
        return entryName.substring(PROFILE_CONFIG_DIRECTORY.length());
    }

    public static String backupConfigEntry(String relativePath) {
        return BACKUP_CONFIG_DIRECTORY + normalizeArchivePath(relativePath);
    }

    public static boolean isBackupOriginalEntry(String entryName) {
        return entryName != null && entryName.startsWith(BACKUP_ORIGINAL_DIRECTORY);
    }

    public static String backupOriginalRelativePath(String entryName) {
        return entryName.substring(BACKUP_ORIGINAL_DIRECTORY.length());
    }

    private static String normalizeArchivePath(String path) {
        return path.replace('\\', '/');
    }
}
