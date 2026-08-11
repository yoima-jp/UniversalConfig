package com.example.universalconfig.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Issue #24: retention-based cleanup of generated files under the configured root directory.
 *
 * <p>Backups ({@code backups/*.ucbackup}) and launch logs ({@code logs/launches/*.log}) accumulate
 * indefinitely. This cleaner deletes files older than a retention window while preserving files that
 * cannot be safely classified as expired.
 *
 * <p>Design decisions enforced here:
 * <ul>
 *   <li>Backups are expired by the {@code createdAt} field of their embedded
 *       {@code backup-manifest.json}. A backup whose manifest is missing, corrupt, unreadable, or has
 *       an unparseable {@code createdAt} is never deleted, because an unreadable archive gives no
 *       evidence that it is safe to remove.</li>
 *   <li>Launch logs are expired by filesystem last-modified time. {@code latest.log} is always
 *       preserved because it is the active rolling log the user inspects after a failure.</li>
 *   <li>{@code restart-helper.log} is intentionally not rotated here. It lives in the restart
 *       sub-directory, is written by the standalone {@link RestartHelper} process, and its lifecycle
 *       is tied to the cross-process restart flow rather than retention. Rotating it from the client
 *       would risk deleting a log the helper is still appending to.</li>
 *   <li>Cleanup failure is logged via {@link FileOperationLogger} and never propagates. Profile
 *       application and startup must not fail because a stale file could not be removed.</li>
 * </ul>
 */
public final class GeneratedFileCleaner {
    /** Default retention window for generated files. */
    public static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final int MAX_MANIFEST_BYTES = 1_048_576;

    private final UniversalConfigSettings settings;
    private final Duration retention;

    public GeneratedFileCleaner(UniversalConfigSettings settings) {
        this(settings, DEFAULT_RETENTION);
    }

    public GeneratedFileCleaner(UniversalConfigSettings settings, Duration retention) {
        this.settings = settings;
        this.retention = retention == null ? DEFAULT_RETENTION : retention;
    }

    /**
     * Runs cleanup for both backups and launch logs.
     *
     * @return number of files actually deleted.
     */
    public int cleanup() {
        int deleted = 0;
        deleted += cleanupBackups();
        deleted += cleanupLaunchLogs();
        FileOperationLogger.info("CLEANUP_SUMMARY", settings.rootDirectory(), "deleted=" + deleted);
        return deleted;
    }

    /**
     * Deletes backups whose manifest {@code createdAt} is valid and older than the retention window.
     *
     * <p>A backup is deleted only when all of the following hold:
     * <ul>
     *   <li>The file is a regular {@code .ucbackup} file inside the configured backups directory.</li>
     *   <li>The ZIP can be opened and every entry passes {@link ZipSecurity} validation.</li>
     *   <li>The {@code backup-manifest.json} entry exists and parses as JSON.</li>
     *   <li>{@code createdAt} parses as an ISO offset date-time and is older than the retention window.</li>
     * </ul>
     * Any failure at any step causes that backup to be skipped and logged, never deleted.
     */
    public int cleanupBackups() {
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings).toAbsolutePath().normalize();
        FileOperationLogger.info("CLEANUP_BACKUPS", backupsDir, "start retention=" + retention);
        if (!Files.isDirectory(backupsDir)) {
            FileOperationLogger.info("CLEANUP_BACKUPS", backupsDir, "directory missing");
            return 0;
        }

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.list(backupsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(UniversalConfigFormat.BACKUP_FILE_EXTENSION))
                    .forEach(candidates::add);
        } catch (IOException ex) {
            FileOperationLogger.failure("CLEANUP_BACKUPS", backupsDir, "failed to list backups", ex);
            return 0;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        int deleted = 0;
        for (Path backup : candidates) {
            OffsetDateTime createdAt = readBackupCreatedAt(backup);
            if (createdAt == null) {
                continue;
            }
            if (createdAt.isBefore(cutoff)) {
                // Reading every payload on every startup would make recent large backups unnecessarily expensive.
                // Full CRC/readability validation is required only immediately before an expired archive is deleted.
                if (isBackupFullyReadable(backup) && deleteSafe(backup, "CLEANUP_BACKUP")) {
                    deleted++;
                }
            } else {
                FileOperationLogger.info("CLEANUP_BACKUP_KEEP", backup, "createdAt=" + createdAt);
            }
        }
        FileOperationLogger.info("CLEANUP_BACKUPS", backupsDir, "deleted=" + deleted);
        return deleted;
    }

    /**
     * Deletes launch logs older than the retention window by last-modified time.
     * {@code latest.log} is never deleted.
     */
    public int cleanupLaunchLogs() {
        Path logsDir = UniversalConfigPaths.logsDirectory(settings)
                .resolve(UniversalConfigFormat.LAUNCH_LOGS_DIRECTORY_NAME)
                .toAbsolutePath().normalize();
        FileOperationLogger.info("CLEANUP_LAUNCH_LOGS", logsDir, "start retention=" + retention);
        if (!Files.isDirectory(logsDir)) {
            FileOperationLogger.info("CLEANUP_LAUNCH_LOGS", logsDir, "directory missing");
            return 0;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(retention);
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> stream = Files.list(logsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))
                    .forEach(candidates::add);
        } catch (IOException ex) {
            FileOperationLogger.failure("CLEANUP_LAUNCH_LOGS", logsDir, "failed to list launch logs", ex);
            return 0;
        }

        int deleted = 0;
        for (Path log : candidates) {
            String name = log.getFileName().toString();
            if (UniversalConfigFormat.LATEST_LOG_FILE_NAME.equals(name)) {
                continue;
            }
            try {
                OffsetDateTime lastModified = OffsetDateTime.ofInstant(
                        Files.getLastModifiedTime(log).toInstant(),
                        OffsetDateTime.now().getOffset());
                if (lastModified.isBefore(cutoff)) {
                    if (deleteSafe(log, "CLEANUP_LAUNCH_LOG")) {
                        deleted++;
                    }
                }
            } catch (IOException ex) {
                FileOperationLogger.failure("CLEANUP_LAUNCH_LOG", log, "could not read last-modified time", ex);
            }
        }
        FileOperationLogger.info("CLEANUP_LAUNCH_LOGS", logsDir, "deleted=" + deleted);
        return deleted;
    }

    /**
     * Reads and validates the {@code createdAt} timestamp from a backup archive.
     * Returns {@code null} if the archive is corrupt, unreadable, lacks a manifest,
     * or has an unparseable timestamp. In all those cases the backup must not be deleted.
     */
    private OffsetDateTime readBackupCreatedAt(Path backup) {
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings).toAbsolutePath().normalize();
        Path normalized = backup.toAbsolutePath().normalize();
        if (!normalized.startsWith(backupsDir)) {
            FileOperationLogger.failure("CLEANUP_BACKUP", backup, "outside backups directory", null);
            return null;
        }

        try (ZipFile zip = new ZipFile(normalized.toFile())) {
            // Validate every entry name through ZipSecurity to reject unsafe archives early.
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                ZipSecurity.validateRelativeEntryName(entry.getName());
            }
            ZipEntry manifestEntry = zip.getEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY);
            if (manifestEntry == null || manifestEntry.isDirectory()) {
                FileOperationLogger.failure("CLEANUP_BACKUP", backup, "manifest missing", null);
                return null;
            }
            long manifestSize = manifestEntry.getSize();
            if (manifestSize < 0 || manifestSize > MAX_MANIFEST_BYTES) {
                FileOperationLogger.failure("CLEANUP_BACKUP", backup, "manifest size is unsafe", null);
                return null;
            }
            BackupManifest manifest;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                byte[] encoded = readUpTo(input, MAX_MANIFEST_BYTES + 1);
                if (encoded.length > MAX_MANIFEST_BYTES) {
                    FileOperationLogger.failure("CLEANUP_BACKUP", backup, "manifest exceeds size limit", null);
                    return null;
                }
                manifest = JsonDocuments.GSON.fromJson(new String(encoded, StandardCharsets.UTF_8), BackupManifest.class);
            }
            if (manifest == null
                    || !UniversalConfigFormat.BACKUP_FORMAT.equals(manifest.format)
                    || manifest.formatVersion != UniversalConfigFormat.FORMAT_VERSION) {
                FileOperationLogger.failure("CLEANUP_BACKUP", backup, "manifest format is invalid", null);
                return null;
            }
            if (manifest.createdAt == null || manifest.createdAt.trim().isEmpty()) {
                FileOperationLogger.failure("CLEANUP_BACKUP", backup, "manifest has no createdAt", null);
                return null;
            }
            try {
                return OffsetDateTime.parse(manifest.createdAt);
            } catch (DateTimeParseException ex) {
                FileOperationLogger.failure("CLEANUP_BACKUP", backup, "unparseable createdAt: " + manifest.createdAt, ex);
                return null;
            }
        } catch (IOException | UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("CLEANUP_BACKUP", backup, "unreadable or corrupt backup", ex);
            return null;
        }
    }

    private boolean isBackupFullyReadable(Path backup) {
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings).toAbsolutePath().normalize();
        Path normalized = backup.toAbsolutePath().normalize();
        if (!normalized.startsWith(backupsDir)) {
            FileOperationLogger.failure("CLEANUP_BACKUP", backup, "outside backups directory", null);
            return false;
        }
        try (InputStream fileInput = Files.newInputStream(normalized);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(fileInput), StandardCharsets.UTF_8)) {
            verifyAllEntriesReadable(zip);
            return true;
        } catch (IOException | UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("CLEANUP_BACKUP", backup, "backup payload is unreadable or corrupt", ex);
            return false;
        }
    }

    private void verifyAllEntriesReadable(ZipInputStream zip) throws IOException, UniversalConfigException {
        // Reads every non-directory ZIP entry to EOF with a fixed-size buffer.
        // A corrupt payload entry can have a valid manifest yet still be unusable, so the
        // entire archive must be verified before it is considered safe to delete.
        // readAllBytes is intentionally avoided because .ucbackup is untrusted external input.
        // ZipInputStream verifies each entry's CRC when it reaches EOF; ZipFile entry streams do not.
        byte[] buffer = new byte[8192];
        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            ZipSecurity.validateRelativeEntryName(entry.getName());
            if (!entry.isDirectory()) {
                while (zip.read(buffer) != -1) {
                    // Drain to EOF.
                }
            }
            zip.closeEntry();
            entry = zip.getNextEntry();
        }
    }

    private static byte[] readUpTo(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        while (output.size() < maximumBytes) {
            int remaining = maximumBytes - output.size();
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean deleteSafe(Path path, String operation) {
        try {
            Files.deleteIfExists(path);
            FileOperationLogger.info(operation, path, "deleted expired");
            return true;
        } catch (IOException ex) {
            FileOperationLogger.failure(operation, path, "delete failed", ex);
            return false;
        }
    }
}
