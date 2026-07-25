package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ProfileService {
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
            Files.writeString(marker, "", StandardCharsets.UTF_8,
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
            return List.of();
        }
        try (var stream = Files.list(profiles)) {
            List<ProfileSummary> summaries = new ArrayList<>();
            for (Path profile : stream.filter(path -> path.getFileName().toString().endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION)).toList()) {
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
            return List.of();
        }
        try (var stream = Files.list(backups)) {
            List<BackupSummary> summaries = new ArrayList<>();
            for (Path backup : stream.filter(path -> path.getFileName().toString().endsWith(UniversalConfigFormat.BACKUP_FILE_EXTENSION)).toList()) {
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
        manifest.name = options.name;
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

        Path destination = uniqueProfilePath(manifest.id);
        FileOperationLogger.info("CREATE_PROFILE", destination, "name=" + manifest.name);
        try (ProfileArchiveWriter.InMemory writer = new ProfileArchiveWriter.InMemory()) {
            writer.addString(UniversalConfigFormat.MANIFEST_ENTRY, JsonDocuments.toJson(manifest));
            adapterRegistry.adapterFor(instancePath).exportProfile(instancePath, writer, options, environment);
            writer.addString(UniversalConfigFormat.PROFILE_README_ENTRY, "Universal Config profile. Apply only after reviewing warnings and creating a backup.\n");
            ChecksumDocument checksums = Checksums.create(writer.pendingEntries());
            writer.addString(UniversalConfigFormat.CHECKSUMS_ENTRY, JsonDocuments.toJson(checksums));
            ZipArchiveWriter.write(destination, writer.pendingEntries());
            FileOperationLogger.info("CREATE_PROFILE", destination, "complete entries=" + writer.pendingEntries().size());
            return destination;
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
                || pending.profilePath.isBlank()) {
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
        Path profilePath = Path.of(pending.profilePath);
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
        try {
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
        Path destination = uniqueProfilePath(UniversalConfigPaths.safeFileSlug(manifest.id + "-copy"));
        try {
            Files.copy(profilePath, destination);
            FileOperationLogger.info("DUPLICATE_PROFILE", destination, "from=" + profilePath.toAbsolutePath().normalize());
            return destination;
        } catch (IOException ex) {
            FileOperationLogger.failure("DUPLICATE_PROFILE", destination, "from=" + profilePath.toAbsolutePath().normalize(), ex);
            throw new UniversalConfigException("Failed to duplicate profile.", ex);
        }
    }

    public Path exportProfile(Path profilePath, Path destinationDirectory) throws UniversalConfigException {
        try {
            Files.createDirectories(destinationDirectory);
            FileOperationLogger.info("CREATE_DIRECTORY", destinationDirectory, "export destination");
            Path destination = destinationDirectory.resolve(profilePath.getFileName());
            Files.copy(profilePath, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            FileOperationLogger.info("EXPORT_PROFILE", destination, "from=" + profilePath.toAbsolutePath().normalize());
            return destination;
        } catch (IOException ex) {
            FileOperationLogger.failure("EXPORT_PROFILE", destinationDirectory, "failed", ex);
            throw new UniversalConfigException("Failed to export profile.", ex);
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
        if (options.name == null || options.name.isBlank()) {
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

    public record ApplyResult(Path backupPath, ProfileDiff diff) {
    }
}
