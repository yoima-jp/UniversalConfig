package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #24: tests for {@link GeneratedFileCleaner}.
 * Covers retention, valid deletion, corrupt-backup preservation, latest.log preservation,
 * custom root, and failure-safe behavior.
 */
class GeneratedFileCleanerTest {
    @TempDir
    Path tempDir;

    @Test
    void deletesOldBackupWithValidCreatedAt() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        OffsetDateTime oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(45);
        Path oldBackup = createBackupZip(backupsDir.resolve("old.ucbackup"), oldDate);

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(1, deleted);
        assertFalse(Files.exists(oldBackup));
    }

    @Test
    void keepsRecentBackup() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        OffsetDateTime recentDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
        Path recentBackup = createBackupZip(backupsDir.resolve("recent.ucbackup"), recentDate);

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(recentBackup));
    }

    @Test
    void preservesCorruptBackup() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        // Write a file with the .ucbackup extension that is not a valid ZIP.
        Path corrupt = backupsDir.resolve("corrupt.ucbackup");
        Files.writeString(corrupt, "this is not a zip", StandardCharsets.UTF_8);

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(corrupt), "corrupt backup must not be deleted");
    }

    @Test
    void preservesBackupWithMissingManifest() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        // Create a valid ZIP but without the backup-manifest.json entry.
        Path noManifest = backupsDir.resolve("no-manifest.ucbackup");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(noManifest))) {
            zip.putNextEntry(new ZipEntry("original/options.txt"));
            zip.write("guiScale:3\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(noManifest));
    }

    @Test
    void preservesBackupWithUnparseableCreatedAt() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        OffsetDateTime oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(60);
        Path badDate = createBackupZipWithCreatedAt(backupsDir.resolve("baddate.ucbackup"), "not-a-date");

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(badDate));
    }

    @Test
    void preservesBackupWithUnexpectedFormat() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        Path invalidFormat = backupsDir.resolve("invalid-format.ucbackup");
        String manifestJson = "{"
                + "\"format\":\"not-a-universal-config-backup\","
                + "\"formatVersion\":" + UniversalConfigFormat.FORMAT_VERSION + ","
                + "\"createdAt\":\"" + OffsetDateTime.now(ZoneOffset.UTC).minusDays(60) + "\""
                + "}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(invalidFormat))) {
            zip.putNextEntry(new ZipEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(invalidFormat));
    }

    @Test
    void preservesBackupWithEmptyCreatedAt() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        Path emptyDate = createBackupZipWithCreatedAt(backupsDir.resolve("empty.ucbackup"), "");

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(emptyDate));
    }

    @Test
    void deletesOldLaunchLogButPreservesLatest() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path launchesDir = UniversalConfigPaths.logsDirectory(settings)
                .resolve(UniversalConfigFormat.LAUNCH_LOGS_DIRECTORY_NAME);
        Files.createDirectories(launchesDir);

        Path oldLog = launchesDir.resolve(UniversalConfigFormat.LAUNCH_LOG_FILE_PREFIX + "20250101-120000-000-pid1.log");
        Files.writeString(oldLog, "old", StandardCharsets.UTF_8);
        setLastModifiedDaysAgo(oldLog, 45);

        Path latestLog = launchesDir.resolve(UniversalConfigFormat.LATEST_LOG_FILE_NAME);
        Files.writeString(latestLog, "latest", StandardCharsets.UTF_8);
        setLastModifiedDaysAgo(latestLog, 45);

        Path recentLog = launchesDir.resolve(UniversalConfigFormat.LAUNCH_LOG_FILE_PREFIX + "20260101-120000-000-pid2.log");
        Files.writeString(recentLog, "recent", StandardCharsets.UTF_8);
        setLastModifiedDaysAgo(recentLog, 2);

        int deleted = new GeneratedFileCleaner(settings).cleanupLaunchLogs();

        assertEquals(1, deleted);
        assertFalse(Files.exists(oldLog));
        assertTrue(Files.exists(latestLog), "latest.log must always be preserved");
        assertTrue(Files.exists(recentLog));
    }

    @Test
    void cleanupDoesNothingWhenDirectoriesMissing() {
        UniversalConfigSettings settings = newSettings();

        int deleted = new GeneratedFileCleaner(settings).cleanup();

        assertEquals(0, deleted);
    }

    @Test
    void customRootCleansOwnDirectories() throws Exception {
        Path customRoot = tempDir.resolve("custom-root");
        UniversalConfigSettings settings = new UniversalConfigSettings(customRoot);
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Path launchesDir = UniversalConfigPaths.logsDirectory(settings)
                .resolve(UniversalConfigFormat.LAUNCH_LOGS_DIRECTORY_NAME);
        Files.createDirectories(backupsDir);
        Files.createDirectories(launchesDir);

        OffsetDateTime oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(40);
        Path oldBackup = createBackupZip(backupsDir.resolve("old.ucbackup"), oldDate);

        Path oldLog = launchesDir.resolve("old.log");
        Files.writeString(oldLog, "old", StandardCharsets.UTF_8);
        setLastModifiedDaysAgo(oldLog, 40);

        int deleted = new GeneratedFileCleaner(settings).cleanup();

        assertEquals(2, deleted);
        assertFalse(Files.exists(oldBackup));
        assertFalse(Files.exists(oldLog));
    }

    @Test
    void customRetentionWindowDeletesRecentlyCreatedFiles() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        // A backup 5 days old is kept under the 30-day default but deleted under a 1-day window.
        OffsetDateTime fiveDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
        Path backup = createBackupZip(backupsDir.resolve("five-days.ucbackup"), fiveDaysAgo);

        int deleted = new GeneratedFileCleaner(settings, Duration.ofDays(1)).cleanupBackups();

        assertEquals(1, deleted);
        assertFalse(Files.exists(backup));
    }

    @Test
    void cleanupIsFailureSafeWhenBackupDirectoryIsAFile() throws Exception {
        UniversalConfigSettings settings = newSettings();
        // Make the backups "directory" a regular file, causing Files.list to fail.
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir.getParent());
        Files.writeString(backupsDir, "not a directory", StandardCharsets.UTF_8);

        // Should not throw.
        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
    }

    @Test
    void emptyCreatedAtFieldPreservesBackup() throws Exception {
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        // Manifest with null createdAt (Gson leaves the field absent when omitted from JSON).
        Path noDate = backupsDir.resolve("no-created-at.ucbackup");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(noDate))) {
            zip.putNextEntry(new ZipEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY));
            zip.write("{\"format\":\"universal-config-backup\",\"formatVersion\":1}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(noDate));
    }


    @Test
    void preservesOldBackupWithCorruptNonManifestEntry() throws Exception {
        // A backup with a valid manifest but a CRC-mismatched payload entry must not be deleted,
        // because the archive is partially corrupt and may be needed for recovery.
        UniversalConfigSettings settings = newSettings();
        Path backupsDir = UniversalConfigPaths.backupsDirectory(settings);
        Files.createDirectories(backupsDir);

        OffsetDateTime oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(60);
        Path corruptPayload = backupsDir.resolve("corrupt-payload.ucbackup");
        // Build a valid ZIP first, then corrupt one byte in the payload data so the stored
        // CRC no longer matches the decompressed content. ZipFile detects this on read.
        createBackupZipWithPayload(corruptPayload, oldDate);
        corruptZipPayloadByte(corruptPayload);

        int deleted = new GeneratedFileCleaner(settings).cleanupBackups();

        assertEquals(0, deleted);
        assertTrue(Files.exists(corruptPayload), "backup with corrupt payload entry must be preserved");
    }

    // --- helpers ---

    private UniversalConfigSettings newSettings() {
        return new UniversalConfigSettings(tempDir.resolve("shared"));
    }

    private Path createBackupZip(Path target, OffsetDateTime createdAt) throws IOException {
        return createBackupZipWithCreatedAt(target,
                createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    private Path createBackupZipWithCreatedAt(Path target, String createdAtValue) throws IOException {
        Files.createDirectories(target.getParent());
        String manifestJson = "{"
                + "\"format\":\"" + UniversalConfigFormat.BACKUP_FORMAT + "\","
                + "\"formatVersion\":" + UniversalConfigFormat.FORMAT_VERSION + ","
                + "\"createdAt\":\"" + createdAtValue + "\","
                + "\"instancePath\":\"test\","
                + "\"files\":[]"
                + "}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return target;
    }

    private void setLastModifiedDaysAgo(Path path, long daysAgo) throws IOException {
        OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysAgo);
        Files.setLastModifiedTime(path, FileTime.from(time.toInstant()));
    }

    private Path createBackupZipWithPayload(Path target, OffsetDateTime createdAt) throws IOException {
        Files.createDirectories(target.getParent());
        String manifestJson = "{"
                + "\"format\":\"" + UniversalConfigFormat.BACKUP_FORMAT + "\","
                + "\"formatVersion\":" + UniversalConfigFormat.FORMAT_VERSION + ","
                + "\"createdAt\":\"" + createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\","
                + "\"instancePath\":\"test\","
                + "\"files\":[]"
                + "}";
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY));
            zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] payload = "guiScale:3\n".getBytes(StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            crc.update(payload);
            ZipEntry payloadEntry = new ZipEntry(UniversalConfigFormat.BACKUP_ORIGINAL_DIRECTORY + "options.txt");
            payloadEntry.setMethod(ZipEntry.STORED);
            payloadEntry.setSize(payload.length);
            payloadEntry.setCompressedSize(payload.length);
            payloadEntry.setCrc(crc.getValue());
            zip.putNextEntry(payloadEntry);
            zip.write(payload);
            zip.closeEntry();
        }
        return target;
    }

    private void corruptZipPayloadByte(Path zipFile) throws IOException {
        // The payload entry is STORED, so its bytes appear verbatim exactly once. Flipping that byte keeps the ZIP
        // structure and manifest readable while guaranteeing a CRC failure in the non-manifest entry.
        byte[] data = Files.readAllBytes(zipFile);
        byte[] payload = "guiScale:3\n".getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(data, payload);
        if (offset < 0) {
            throw new IOException("Could not locate stored test payload.");
        }
        data[offset] = (byte) (data[offset] ^ 0xFF);
        Files.write(zipFile, data);
    }

    private int indexOf(byte[] data, byte[] target) {
        for (int index = 0; index <= data.length - target.length; index++) {
            boolean match = true;
            for (int targetIndex = 0; targetIndex < target.length; targetIndex++) {
                if (data[index + targetIndex] != target[targetIndex]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return index;
            }
        }
        return -1;
    }
}
