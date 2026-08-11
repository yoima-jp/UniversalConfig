package com.example.universalconfig.core;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProfileService {
    private static final ReentrantLock PROFILE_OPERATION_PROCESS_LOCK = new ReentrantLock();
    private static final int MAX_MANIFEST_BYTES = 1_048_576;
    private final UniversalConfigSettings settings;
    private final AdapterRegistry adapterRegistry = new AdapterRegistry();

    public ProfileService(UniversalConfigSettings settings) {
        this.settings = settings;
    }

    public UniversalConfigSettings settings() {
        return settings;
    }

    public Path resolveDefaultProfile(Path instancePath) {
        Path configured = settings.defaultProfilePath();
        if (configured == null) {
            return null;
        }

        Path normalized = configured.toAbsolutePath().normalize();
        if (!isValidProfilePath(normalized) || !Files.isRegularFile(normalized)) {
            FileOperationLogger.failure("CLEAR_INVALID_DEFAULT_PROFILE", normalized,
                    "missing or outside profiles directory",
                    new UniversalConfigException("Configured default profile is unavailable."));
            settings.setDefaultProfilePath(null);
            try {
                UniversalConfigPaths.saveSettings(instancePath, settings);
            } catch (UniversalConfigException ex) {
                FileOperationLogger.failure("CLEAR_INVALID_DEFAULT_PROFILE", normalized, "failed to persist clear", ex);
            }
            return null;
        }

        return normalized;
    }

    public boolean isDefaultProfile(Path profilePath) {
        Path configured = settings.defaultProfilePath();
        return configured != null
                && profilePath != null
                && configured.toAbsolutePath().normalize().equals(profilePath.toAbsolutePath().normalize());
    }

    public void setDefaultProfile(Path instancePath, Path profilePath) throws UniversalConfigException {
        Path normalized = validateProfilePath(profilePath);
        settings.setDefaultProfilePath(normalized);
        UniversalConfigPaths.saveSettings(instancePath, settings);
        FileOperationLogger.info("SET_DEFAULT_PROFILE", normalized, "default profile saved");
    }

    public void clearDefaultProfile(Path instancePath) throws UniversalConfigException {
        Path previous = settings.defaultProfilePath();
        if (previous == null) {
            return;
        }
        settings.setDefaultProfilePath(null);
        UniversalConfigPaths.saveSettings(instancePath, settings);
        FileOperationLogger.info("CLEAR_DEFAULT_PROFILE", previous, "default profile cleared");
    }

    /**
     * Issue #12: a shared default is imported only during an instance's first-run onboarding.
     * Manual pending imports always win, and the marker is written only after apply() completes.
     */
    public ApplyResult applyDefaultProfileOnFirstStart(
            Path instancePath,
            MinecraftEnvironment environment
    ) throws UniversalConfigException {
        Path marker = UniversalConfigPaths.defaultProfileAppliedMarker(instancePath);
        if (Files.exists(marker)) {
            FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", marker, "already applied in this instance");
            return null;
        }
        if (readPendingImport(instancePath) != null) {
            FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", instancePath, "manual pending import takes priority");
            return null;
        }
        if (!isFirstMinecraftStart(instancePath)) {
            FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", instancePath, "not first start");
            return null;
        }

        Path defaultProfile = resolveDefaultProfile(instancePath);
        if (defaultProfile == null) {
            FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", instancePath, "no default profile configured");
            return null;
        }

        FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", defaultProfile, "start");
        ApplyResult result = apply(instancePath, defaultProfile, environment);
        writeDefaultProfileAppliedMarker(marker);
        FileOperationLogger.info("AUTO_APPLY_DEFAULT_PROFILE", defaultProfile, "complete marker=" + marker);
        return result;
    }

    boolean isFirstMinecraftStart(Path instancePath) throws UniversalConfigException {
        Path options = UniversalConfigPaths.optionsFile(instancePath);
        if (!Files.exists(options)) {
            return true;
        }
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                int separator = line.indexOf(':');
                if (separator > 0
                        && "onboardAccessibility".equals(line.substring(0, separator).trim())
                        && Boolean.parseBoolean(line.substring(separator + 1).trim())) {
                    return true;
                }
            }
            return false;
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to inspect Minecraft first-start state.", ex);
        }
    }

    private void writeDefaultProfileAppliedMarker(Path marker) throws UniversalConfigException {
        try {
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[0],
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new UniversalConfigException("Default profile was applied, but its marker could not be saved.", ex);
        }
    }

    public List<ProfileSummary> listProfiles() throws UniversalConfigException {
        Path profiles = UniversalConfigPaths.profilesDirectory(settings);
        FileOperationLogger.info("LIST_PROFILES", profiles, "start");
        if (!Files.isDirectory(profiles)) {
            FileOperationLogger.info("LIST_PROFILES", profiles, "directory missing");
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(profiles)) {
            List<ProfileSummary> summaries = new ArrayList<>();
            for (Path profile : stream.filter(path -> path.getFileName().toString().endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION)).collect(Collectors.toList())) {
                try (ZipArchiveReader reader = new ZipArchiveReader(profile)) {
                    if (reader.exists(UniversalConfigFormat.MANIFEST_ENTRY)) {
                        summaries.add(new ProfileSummary(profile, JsonDocuments.read(reader, UniversalConfigFormat.MANIFEST_ENTRY, ProfileManifest.class)));
                    }
                } catch (IOException | UniversalConfigException ignored) {
                    // Broken profiles are intentionally skipped here; loading surfaces detailed errors.
                }
            }
            summaries.sort(Comparator.comparing((ProfileSummary summary) -> safeUpdatedAt(summary.manifest())).reversed());
            FileOperationLogger.info("LIST_PROFILES", profiles, "count=" + summaries.size());
            return summaries;
        } catch (IOException ex) {
            FileOperationLogger.failure("LIST_PROFILES", profiles, "failed", ex);
            throw new UniversalConfigException("Failed to list profiles.", ex);
        }
    }

    public List<BackupSummary> listBackups() throws UniversalConfigException {
        Path backups = UniversalConfigPaths.backupsDirectory(settings);
        FileOperationLogger.info("LIST_BACKUPS", backups, "start");
        if (!Files.isDirectory(backups)) {
            FileOperationLogger.info("LIST_BACKUPS", backups, "directory missing");
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(backups)) {
            List<BackupSummary> summaries = new ArrayList<>();
            for (Path backup : stream.filter(path -> path.getFileName().toString().endsWith(UniversalConfigFormat.BACKUP_FILE_EXTENSION)).collect(Collectors.toList())) {
                try (ZipArchiveReader reader = new ZipArchiveReader(backup)) {
                    BackupManifest manifest = reader.exists(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY)
                            ? JsonDocuments.read(reader, UniversalConfigFormat.BACKUP_MANIFEST_ENTRY, BackupManifest.class)
                            : new BackupManifest();
                    summaries.add(new BackupSummary(backup, manifest, reader.entries()));
                } catch (IOException | UniversalConfigException ignored) {
                    // Broken backups are skipped in the list and fail explicitly when opened.
                }
            }
            summaries.sort(Comparator.comparing((BackupSummary summary) -> safeCreatedAt(summary.manifest())).reversed());
            FileOperationLogger.info("LIST_BACKUPS", backups, "count=" + summaries.size());
            return summaries;
        } catch (IOException ex) {
            FileOperationLogger.failure("LIST_BACKUPS", backups, "failed", ex);
            throw new UniversalConfigException("Failed to list backups.", ex);
        }
    }

    public Path createProfile(Path instancePath, ProfileCreateOptions options, MinecraftEnvironment environment)
            throws UniversalConfigException {
        validateCreateOptions(options);
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        ProfileManifest manifest = new ProfileManifest();
        manifest.id = UniversalConfigPaths.safeFileSlug(options.name);
        manifest.description = options.description == null ? "" : options.description;
        // Profiles can be imported from outside the client UI. Keep only known cosmetic IDs in manifests.
        manifest.icon = ProfileIcon.normalize(options.icon);
        manifest.createdAt = now;
        manifest.updatedAt = now;
        manifest.source.minecraftVersion = environment.minecraftVersion();
        manifest.source.loader = environment.loaderId();
        manifest.source.loaderVersion = environment.loaderVersion();
        manifest.compatibility.testedVersions.add(environment.minecraftVersion());
        manifest.includes.keybinds = options.includeKeybinds;
        manifest.includes.clientOptions = options.includeClientOptions;
        manifest.includes.modConfigs = options.includeModConfigs;

        Path destination = null;
        try (ProfileDirectoryLock ignored = lockProfileDirectory()) {
            manifest.name = uniqueProfileName(options.name);
            destination = uniqueProfilePath(manifest.id);
            FileOperationLogger.info("CREATE_PROFILE", destination, "name=" + manifest.name);
            try (ProfileArchiveWriter.InMemory writer = new ProfileArchiveWriter.InMemory()) {
                writer.addString(UniversalConfigFormat.MANIFEST_ENTRY, JsonDocuments.toJson(manifest));
                adapterRegistry.adapterFor(instancePath).exportProfile(instancePath, writer, options, environment);
                writer.addString(UniversalConfigFormat.PROFILE_README_ENTRY, "Universal Config profile. Apply only after reviewing warnings and creating a backup.\n");
                ChecksumDocument checksums = Checksums.create(writer.pendingEntries());
                writer.addString(UniversalConfigFormat.CHECKSUMS_ENTRY, JsonDocuments.toJson(checksums));
                Path temporary = Files.createTempFile(destination.getParent(), ".universal-config-profile-", ".tmp");
                try {
                    ZipArchiveWriter.write(temporary, writer.pendingEntries());
                    moveNewProfile(temporary, destination);
                } finally {
                    deleteTemporaryProfile(temporary);
                }
                FileOperationLogger.info("CREATE_PROFILE", destination, "complete entries=" + writer.pendingEntries().size());
                return destination;
            }
        } catch (IOException ex) {
            FileOperationLogger.failure("CREATE_PROFILE", destination, "failed", ex);
            throw new UniversalConfigException("Failed to create profile.", ex);
        }
    }

    public ProfileDiff diff(Path instancePath, Path profilePath, MinecraftEnvironment environment) throws UniversalConfigException {
        FileOperationLogger.info("DIFF_PROFILE", profilePath, "instance=" + instancePath.toAbsolutePath().normalize());
        try (ZipArchiveReader reader = new ZipArchiveReader(profilePath)) {
            requireProfile(reader);
            ProfileDiff diff = adapterRegistry.adapterFor(instancePath).diff(instancePath, reader, environment);
            FileOperationLogger.info("DIFF_PROFILE", profilePath, "risk=" + diff.riskLevel);
            return diff;
        } catch (IOException ex) {
            FileOperationLogger.failure("DIFF_PROFILE", profilePath, "failed", ex);
            throw new UniversalConfigException("Failed to diff profile.", ex);
        }
    }

    public ApplyResult apply(Path instancePath, Path profilePath, MinecraftEnvironment environment) throws UniversalConfigException {
        FileOperationLogger.info("APPLY_PROFILE", profilePath, "start instance=" + instancePath.toAbsolutePath().normalize());
        ProfileAdapter adapter = adapterRegistry.adapterFor(instancePath);
        ProfileDiff diff = diff(instancePath, profilePath, environment);
        Path backupPath = adapter.backup(instancePath, settings, environment);
        try (ZipArchiveReader reader = new ZipArchiveReader(profilePath)) {
            requireProfile(reader);
            adapter.importProfile(instancePath, reader, diff);
            FileOperationLogger.info("APPLY_PROFILE", profilePath, "complete backup=" + backupPath.toAbsolutePath().normalize());
            // Issue #24: after a successful backup+apply, opportunistically clean up expired generated files.
            // Cleanup is best-effort and must never cause apply to fail.
            runGeneratedFileCleanup();
            return new ApplyResult(backupPath, diff);
        } catch (IOException ex) {
            FileOperationLogger.failure("APPLY_PROFILE", profilePath, "failed backup=" + backupPath.toAbsolutePath().normalize(), ex);
            throw new UniversalConfigException("Failed to apply profile after backup: " + backupPath, ex);
        }
    }

    public Path scheduleApplyOnNextStart(Path instancePath, Path profilePath, MinecraftEnvironment environment) throws UniversalConfigException {
        readManifest(profilePath);
        diff(instancePath, profilePath, environment);
        PendingImport pending = new PendingImport();
        pending.profilePath = profilePath.toAbsolutePath().normalize().toString();
        pending.scheduledAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        pending.minecraftVersion = environment.minecraftVersion();
        pending.loader = environment.loaderId();
        pending.loaderVersion = environment.loaderVersion();
        Path pendingPath = pendingImportPath(instancePath);
        JsonDocuments.write(pendingPath, pending);
        FileOperationLogger.info("SCHEDULE_PENDING_IMPORT", pendingPath, "profile=" + pending.profilePath);
        return pendingPath;
    }

    public PendingImport readPendingImport(Path instancePath) throws UniversalConfigException {
        Path pendingPath = pendingImportPath(instancePath);
        if (!Files.exists(pendingPath)) {
            return null;
        }
        PendingImport pending = JsonDocuments.read(pendingPath, PendingImport.class);
        if (pending == null
                || !UniversalConfigFormat.PENDING_IMPORT_FORMAT.equals(pending.format)
                || pending.formatVersion != UniversalConfigFormat.FORMAT_VERSION
                || pending.profilePath == null
                || pending.profilePath.trim().isEmpty()) {
            throw new UniversalConfigException("Invalid pending import file: " + pendingPath);
        }
        return pending;
    }

    public ApplyResult applyPendingImport(Path instancePath, MinecraftEnvironment environment) throws UniversalConfigException {
        Path pendingPath = pendingImportPath(instancePath);
        PendingImport pending = readPendingImport(instancePath);
        if (pending == null) {
            return null;
        }
        Path profilePath = Paths.get(pending.profilePath);
        FileOperationLogger.info("APPLY_PENDING_IMPORT", pendingPath, "profile=" + profilePath.toAbsolutePath().normalize());
        ApplyResult result = apply(instancePath, profilePath, environment);
        try {
            Files.deleteIfExists(pendingPath);
            FileOperationLogger.info("DELETE_PENDING_IMPORT", pendingPath, "applied");
        } catch (IOException ex) {
            FileOperationLogger.failure("DELETE_PENDING_IMPORT", pendingPath, "failed", ex);
            throw new UniversalConfigException("Profile applied, but pending import file could not be removed: " + pendingPath, ex);
        }
        return result;
    }

    public void clearPendingImport(Path instancePath) throws UniversalConfigException {
        Path pendingPath = pendingImportPath(instancePath);
        try {
            Files.deleteIfExists(pendingPath);
            FileOperationLogger.info("DELETE_PENDING_IMPORT", pendingPath, "cleared by user");
        } catch (IOException ex) {
            FileOperationLogger.failure("DELETE_PENDING_IMPORT", pendingPath, "clear failed", ex);
            throw new UniversalConfigException("Failed to clear pending import.", ex);
        }
    }

    public void restore(Path instancePath, Path backupPath) throws UniversalConfigException {
        FileOperationLogger.info("RESTORE_BACKUP", backupPath, "start instance=" + instancePath.toAbsolutePath().normalize());
        try (ZipArchiveReader reader = new ZipArchiveReader(backupPath)) {
            if (!reader.exists(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY)) {
                throw new UniversalConfigException(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY + " is missing.");
            }
            adapterRegistry.adapterFor(instancePath).restore(instancePath, reader);
            FileOperationLogger.info("RESTORE_BACKUP", backupPath, "complete");
        } catch (IOException ex) {
            FileOperationLogger.failure("RESTORE_BACKUP", backupPath, "failed", ex);
            throw new UniversalConfigException("Failed to restore backup.", ex);
        }
    }

    public Path pendingImportPath(Path instancePath) {
        return UniversalConfigPaths.pendingImportFile(instancePath);
    }

    public ProfileManifest readManifest(Path profilePath) throws UniversalConfigException {
        FileOperationLogger.info("READ_MANIFEST", profilePath, "start");
        try (ZipArchiveReader reader = new ZipArchiveReader(profilePath)) {
            requireProfile(reader);
            return JsonDocuments.read(reader, UniversalConfigFormat.MANIFEST_ENTRY, ProfileManifest.class);
        } catch (IOException ex) {
            FileOperationLogger.failure("READ_MANIFEST", profilePath, "failed", ex);
            throw new UniversalConfigException("Failed to read profile manifest.", ex);
        }
    }

    public void deleteProfile(Path profilePath) throws UniversalConfigException {
        Path profilesRoot = UniversalConfigPaths.profilesDirectory(settings).toAbsolutePath().normalize();
        Path normalized = profilePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(profilesRoot)
                || !normalized.getFileName().toString().endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION)) {
            throw new UniversalConfigException("Refusing to delete file outside profiles directory: " + profilePath);
        }
        try (ProfileDirectoryLock ignored = lockProfileDirectory()) {
            Files.deleteIfExists(normalized);
            FileOperationLogger.info("DELETE_PROFILE", normalized, "deleteIfExists");
        } catch (IOException ex) {
            FileOperationLogger.failure("DELETE_PROFILE", normalized, "failed", ex);
            throw new UniversalConfigException("Failed to delete profile.", ex);
        }
    }

    public void deleteProfile(Path instancePath, Path profilePath) throws UniversalConfigException {
        boolean wasDefault = isDefaultProfile(profilePath);
        deleteProfile(profilePath);
        if (wasDefault) {
            clearDefaultProfile(instancePath);
        }
    }

    public Path duplicateProfile(Path profilePath) throws UniversalConfigException {
        ProfileManifest manifest = readManifest(profilePath);
        Path destination = null;
        boolean destinationCreated = false;
        try (ProfileDirectoryLock ignored = lockProfileDirectory()) {
            destination = uniqueProfilePath(UniversalConfigPaths.safeFileSlug(manifest.id) + "-copy");
            copyProfile(profilePath, destination);
            destinationCreated = true;
            ProfileManifest copiedManifest = readManifest(destination);
            ensureUniqueProfileName(destination, copiedManifest.name);
            readManifest(destination);
            FileOperationLogger.info("DUPLICATE_PROFILE", destination, "from=" + profilePath.toAbsolutePath().normalize());
            return destination;
        } catch (UniversalConfigException ex) {
            if (destinationCreated) {
                deleteFailedProfile(destination, "DELETE_FAILED_DUPLICATE", ex);
            }
            FileOperationLogger.failure("DUPLICATE_PROFILE", destination,
                    "from=" + profilePath.toAbsolutePath().normalize(), ex);
            throw ex;
        } catch (IOException ex) {
            if (destinationCreated) {
                deleteFailedProfile(destination, "DELETE_FAILED_DUPLICATE", ex);
            }
            FileOperationLogger.failure("DUPLICATE_PROFILE", destination, "from=" + profilePath.toAbsolutePath().normalize(), ex);
            throw new UniversalConfigException("Failed to duplicate profile.", ex);
        }
    }

    public Path importProfile(Path sourceProfilePath) throws UniversalConfigException {
        if (sourceProfilePath == null) {
            throw new UniversalConfigException("A profile file is required.");
        }
        Path source = sourceProfilePath.toAbsolutePath().normalize();
        String fileName = source.getFileName() == null ? "" : source.getFileName().toString();
        if (!Files.isRegularFile(source)
                || !fileName.toLowerCase(Locale.ROOT).endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION)) {
            UniversalConfigException exception = new UniversalConfigException("The selected file is not a .ucp profile.");
            FileOperationLogger.failure("IMPORT_PROFILE", source, "invalid source file", exception);
            throw exception;
        }

        ProfileManifest manifest;
        try {
            // Validate every ZIP entry and the manifest before copying untrusted external files into shared storage.
            manifest = readManifest(source);
        } catch (UniversalConfigException ex) {
            FileOperationLogger.failure("IMPORT_PROFILE", source, "profile validation failed", ex);
            throw ex;
        }

        Path destination = null;
        boolean destinationCreated = false;
        try (ProfileDirectoryLock ignored = lockProfileDirectory()) {
            destination = uniqueProfilePath(UniversalConfigPaths.safeFileSlug(manifest.id));
            copyProfile(source, destination);
            destinationCreated = true;
            // Revalidate after copying so a replacement between confirmation and copy cannot bypass validation.
            ProfileManifest copiedManifest = readManifest(destination);
            ensureUniqueProfileName(destination, copiedManifest.name);
            // Revalidate once more because a duplicate name rewrite also rebuilds the archive and its checksums.
            readManifest(destination);
            FileOperationLogger.info("IMPORT_PROFILE", destination, "from=" + source);
            return destination;
        } catch (UniversalConfigException ex) {
            if (destinationCreated) {
                deleteFailedProfile(destination, "DELETE_FAILED_IMPORT", ex);
            }
            FileOperationLogger.failure("IMPORT_PROFILE", destination, "from=" + source, ex);
            throw ex;
        } catch (IOException ex) {
            if (destinationCreated) {
                deleteFailedProfile(destination, "DELETE_FAILED_IMPORT", ex);
            }
            FileOperationLogger.failure("IMPORT_PROFILE", destination, "from=" + source, ex);
            throw new UniversalConfigException("Failed to add the profile to shared storage.", ex);
        }
    }

    private void requireProfile(ProfileArchiveReader reader) throws UniversalConfigException {
        if (!reader.exists(UniversalConfigFormat.MANIFEST_ENTRY)) {
            throw new UniversalConfigException(UniversalConfigFormat.MANIFEST_ENTRY + " is missing.");
        }
        ProfileManifest manifest = JsonDocuments.read(reader, UniversalConfigFormat.MANIFEST_ENTRY, ProfileManifest.class);
        if (manifest == null
                || !UniversalConfigFormat.PROFILE_FORMAT.equals(manifest.format)
                || manifest.formatVersion != UniversalConfigFormat.FORMAT_VERSION) {
            throw new UniversalConfigException("Unsupported Universal Config profile format.");
        }
    }

    private Path uniqueProfilePath(String slug) throws UniversalConfigException {
        Path directory = UniversalConfigPaths.profilesDirectory(settings);
        try {
            Files.createDirectories(directory);
            FileOperationLogger.info("CREATE_DIRECTORY", directory, "profiles directory");
        } catch (IOException ex) {
            FileOperationLogger.failure("CREATE_DIRECTORY", directory, "profiles directory", ex);
            throw new UniversalConfigException("Failed to create profiles directory.", ex);
        }
        Path candidate = directory.resolve(slug + UniversalConfigFormat.PROFILE_FILE_EXTENSION);
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(slug + "-" + counter + UniversalConfigFormat.PROFILE_FILE_EXTENSION);
            counter++;
        }
        return candidate;
    }

    private ProfileDirectoryLock lockProfileDirectory() throws UniversalConfigException {
        Path directory = UniversalConfigPaths.profilesDirectory(settings);
        Path lockPath = directory.resolve(UniversalConfigFormat.PROFILE_OPERATION_LOCK_FILE_NAME);
        FileChannel channel = null;
        PROFILE_OPERATION_PROCESS_LOCK.lock();
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.lock();
            return new ProfileDirectoryLock(lockPath, channel, lock, PROFILE_OPERATION_PROCESS_LOCK);
        } catch (IOException | RuntimeException ex) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException cleanupException) {
                    ex.addSuppressed(cleanupException);
                }
            }
            PROFILE_OPERATION_PROCESS_LOCK.unlock();
            FileOperationLogger.failure("LOCK_PROFILES_DIRECTORY", lockPath, "failed", ex);
            throw new UniversalConfigException("Failed to lock the profiles directory.", ex);
        }
    }

    /**
     * Keeps duplicate profiles distinguishable without renaming existing profiles when the list is reloaded.
     * The selected name is stored in the profile manifest, so deleting one profile does not re-number the others.
     */
    private String uniqueProfileName(String name) throws UniversalConfigException {
        return uniqueProfileName(name, null);
    }

    private String uniqueProfileName(String name, Path excludedProfilePath) throws UniversalConfigException {
        if (name == null || name.trim().isEmpty()) {
            return name;
        }

        Path normalizedExcludedPath = excludedProfilePath == null
                ? null
                : excludedProfilePath.toAbsolutePath().normalize();
        Set<String> usedNames = new HashSet<>();
        for (ProfileSummary summary : listProfiles()) {
            if (normalizedExcludedPath != null
                    && summary.path().toAbsolutePath().normalize().equals(normalizedExcludedPath)) {
                continue;
            }
            ProfileManifest existing = summary.manifest();
            if (existing != null && existing.name != null && !existing.name.trim().isEmpty()) {
                usedNames.add(existing.name);
            }
        }

        if (!usedNames.contains(name)) {
            return name;
        }

        int suffix = 1;
        while (true) {
            String candidate = name + " (" + suffix + ")";
            if (!usedNames.contains(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private void ensureUniqueProfileName(Path profilePath, String originalName) throws UniversalConfigException {
        String uniqueName = uniqueProfileName(originalName, profilePath);
        if (Objects.equals(originalName, uniqueName)) {
            return;
        }
        rewriteProfileName(profilePath, uniqueName);
        FileOperationLogger.info("RENAME_PROFILE_DISPLAY_NAME", profilePath,
                "from=" + originalName + " to=" + uniqueName);
    }

    private void copyProfile(Path source, Path destination) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".universal-config-profile-copy-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            moveNewProfile(temporary, destination);
        } finally {
            deleteTemporaryProfile(temporary);
        }
    }

    private void moveNewProfile(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, destination);
        }
    }

    private void deleteTemporaryProfile(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupException) {
            FileOperationLogger.failure("DELETE_PROFILE_TEMPORARY", temporary, "cleanup failed", cleanupException);
        }
    }

    private void rewriteProfileName(Path profilePath, String name) throws UniversalConfigException {
        try {
            Path temporary = Files.createTempFile(profilePath.getParent(), ".universal-config-profile-", ".tmp");
            try {
                try (ZipArchiveReader reader = new ZipArchiveReader(profilePath);
                     InputStream input = reader.open(UniversalConfigFormat.MANIFEST_ENTRY)) {
                    byte[] originalManifest = IoStreams.readLimited(
                            input, MAX_MANIFEST_BYTES, "Profile manifest");
                    JsonObject manifest = JsonDocuments.GSON.fromJson(
                            new String(originalManifest, StandardCharsets.UTF_8), JsonObject.class);
                    if (manifest == null) {
                        throw new UniversalConfigException("Profile manifest is empty.");
                    }
                    manifest.addProperty("name", name);
                    byte[] updatedManifest = JsonDocuments.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
                    ZipArchiveWriter.writeWithReplacedManifest(temporary, reader, updatedManifest);
                }
                try {
                    Files.move(temporary, profilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, profilePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                deleteTemporaryProfile(temporary);
            }
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to save the profile with a unique name.", ex);
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Failed to update the profile manifest.", ex);
        }
    }

    private void deleteFailedProfile(Path profilePath, String operation, Exception failure) {
        try {
            Files.deleteIfExists(profilePath);
            FileOperationLogger.info(operation, profilePath, "cleanup after failure");
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
            FileOperationLogger.failure(operation, profilePath, "cleanup failed", cleanupException);
        }
    }

    private static final class ProfileDirectoryLock implements AutoCloseable {
        private final Path path;
        private final FileChannel channel;
        private final FileLock lock;
        private final ReentrantLock processLock;

        private ProfileDirectoryLock(Path path, FileChannel channel, FileLock lock, ReentrantLock processLock) {
            this.path = path;
            this.channel = channel;
            this.lock = lock;
            this.processLock = processLock;
        }

        @Override
        public void close() {
            try {
                try {
                    lock.release();
                } catch (IOException ex) {
                    FileOperationLogger.failure("UNLOCK_PROFILES_DIRECTORY", path, "release failed", ex);
                }
                try {
                    channel.close();
                } catch (IOException ex) {
                    FileOperationLogger.failure("UNLOCK_PROFILES_DIRECTORY", path, "close failed", ex);
                }
            } finally {
                processLock.unlock();
            }
        }
    }

    private Path validateProfilePath(Path profilePath) throws UniversalConfigException {
        Path profilesRoot = UniversalConfigPaths.profilesDirectory(settings).toAbsolutePath().normalize();
        Path normalized = profilePath == null ? null : profilePath.toAbsolutePath().normalize();
        if (normalized == null
                || !normalized.startsWith(profilesRoot)
                || !normalized.getFileName().toString().endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION)
                || !Files.isRegularFile(normalized)) {
            throw new UniversalConfigException("The selected profile cannot be used as the default profile.");
        }
        return normalized;
    }

    private boolean isValidProfilePath(Path profilePath) {
        Path profilesRoot = UniversalConfigPaths.profilesDirectory(settings).toAbsolutePath().normalize();
        return profilePath != null
                && profilePath.startsWith(profilesRoot)
                && profilePath.getFileName() != null
                && profilePath.getFileName().toString().endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION);
    }

    private void validateCreateOptions(ProfileCreateOptions options) throws UniversalConfigException {
        if (options == null) {
            throw new UniversalConfigException("Profile options are required.");
        }
        if (options.name == null || options.name.trim().isEmpty()) {
            throw new UniversalConfigException("Profile name is required.");
        }
        if (!options.includeKeybinds && !options.includeClientOptions && !options.includeModConfigs) {
            throw new UniversalConfigException("Select at least one profile content type.");
        }
    }

    private String safeUpdatedAt(ProfileManifest manifest) {
        return manifest == null || manifest.updatedAt == null ? "" : manifest.updatedAt;
    }

    private String safeCreatedAt(BackupManifest manifest) {
        return manifest == null || manifest.createdAt == null ? "" : manifest.createdAt;
    }

    public static final class ApplyResult {
        private final Path backupPath;
        private final ProfileDiff diff;

        public ApplyResult(Path backupPath, ProfileDiff diff) {
            this.backupPath = backupPath;
            this.diff = diff;
        }

        public Path backupPath() {
            return backupPath;
        }

        public ProfileDiff diff() {
            return diff;
        }
    }

    /**
     * Issue #24: runs generated-file cleanup, swallowing all failures so callers never break.
     */
    void runGeneratedFileCleanup() {
        try {
            new GeneratedFileCleaner(settings).cleanup();
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("CLEANUP", settings.rootDirectory(), "cleanup failed", ex);
        }
    }
}
