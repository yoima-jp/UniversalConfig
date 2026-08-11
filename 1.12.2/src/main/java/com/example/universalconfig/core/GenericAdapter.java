package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GenericAdapter implements ProfileAdapter {
    @Override
    public boolean detect(Path instancePath) {
        return Files.exists(optionsPath(instancePath)) || Files.isDirectory(configPath(instancePath));
    }

    @Override
    public void exportProfile(Path instancePath, ProfileArchiveWriter writer, ProfileCreateOptions options, MinecraftEnvironment environment)
        throws UniversalConfigException {
        Path optionsPath = optionsPath(instancePath);
        if (options.includeKeybinds) {
            KeybindsDocument keybinds = extractKeybinds(optionsPath);
            writerAddJson(writer, UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY, keybinds);
            FileOperationLogger.info("EXPORT_KEYBINDS", optionsPath, "bindings=" + keybinds.bindings.size());
        }

        if (options.includeClientOptions) {
            OptionsFragmentsDocument fragments = extractOptionsFragments(optionsPath);
            writerAddJson(writer, UniversalConfigFormat.PROFILE_OPTIONS_ENTRY, fragments);
            FileOperationLogger.info("EXPORT_OPTIONS_FRAGMENTS", optionsPath, "options=" + fragments.options.size());
        }

        if (options.includeModConfigs) {
            for (String relative : selectConfigFiles(instancePath, options)) {
                Path source = safeConfigPath(instancePath, relative);
                try {
                    byte[] bytes = Files.readAllBytes(source);
                    FileOperationLogger.info("READ_CONFIG", source, "bytes=" + bytes.length);
                    writer.addBytes(UniversalConfigFormat.profileConfigEntry(relative), bytes);
                    FileOperationLogger.info("EXPORT_CONFIG", source, relative);
                } catch (IOException ex) {
                    FileOperationLogger.failure("EXPORT_CONFIG", source, relative, ex);
                    throw new UniversalConfigException("Failed to add config file to profile: " + relative, ex);
                }
            }
        }
    }

    @Override
    public void importProfile(Path instancePath, ProfileArchiveReader reader, ProfileDiff diff) throws UniversalConfigException {
        Path optionsPath = optionsPath(instancePath);
        if (reader.exists(UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY)) {
            KeybindsDocument keybinds = JsonDocuments.read(reader, UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY, KeybindsDocument.class);
            applyKeybinds(optionsPath, keybinds, diff);
        }

        if (reader.exists(UniversalConfigFormat.PROFILE_OPTIONS_ENTRY)) {
            OptionsFragmentsDocument fragments = JsonDocuments.read(reader, UniversalConfigFormat.PROFILE_OPTIONS_ENTRY, OptionsFragmentsDocument.class);
            applyOptionsFragments(optionsPath, fragments, diff);
        }

        for (String entry : reader.entries()) {
            if (!UniversalConfigFormat.isProfileConfigEntry(entry)) {
                continue;
            }
            String relative = UniversalConfigFormat.profileConfigRelativePath(entry);
            if (isUniversalConfigInternalPath(relative)) {
                diff.skippedItems.add(configDisplayPath(relative) + " (Universal Config internal file)");
                FileOperationLogger.info("SKIP_INTERNAL_CONFIG", configPath(instancePath).resolve(relative), entry);
                continue;
            }
            Path target = safeConfigPath(instancePath, relative);
            if (!Files.exists(target.getParent())) {
                try {
                    Files.createDirectories(target.getParent());
                    FileOperationLogger.info("CREATE_DIRECTORY", target.getParent(), "config target parent");
                } catch (IOException ex) {
                    FileOperationLogger.failure("CREATE_DIRECTORY", target.getParent(), "config target parent", ex);
                    throw new UniversalConfigException("Failed to create config target directory.", ex);
                }
            }
            try (InputStream input = reader.open(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                FileOperationLogger.info("APPLY_CONFIG", target, entry);
            } catch (IOException ex) {
                FileOperationLogger.failure("APPLY_CONFIG", target, entry, ex);
                throw new UniversalConfigException("Failed to apply config file: " + relative, ex);
            }
        }
    }

    @Override
    public ProfileDiff diff(Path instancePath, ProfileArchiveReader reader, MinecraftEnvironment environment) throws UniversalConfigException {
        ProfileDiff diff = new ProfileDiff();
        ProfileManifest manifest = JsonDocuments.read(reader, UniversalConfigFormat.MANIFEST_ENTRY, ProfileManifest.class);
        addCompatibilityWarnings(diff, manifest, environment);
        verifyChecksums(reader, diff);

        if (reader.exists(UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY)) {
            KeybindsDocument keybinds = JsonDocuments.read(reader, UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY, KeybindsDocument.class);
            Map<String, String> current = currentKeybindValues(optionsPath(instancePath));
            boolean modernOptions = usesModernKeybindValues(current, optionsPath(instancePath));
            for (KeybindsDocument.KeyBindingEntry binding : keybinds.bindings) {
                String value = binding.valueForCurrentOptions(modernOptions);
                if (value == null) {
                    diff.skippedItems.add(binding.id + " (no value compatible with current options format)");
                    diff.raiseRisk(RiskLevel.MEDIUM);
                    continue;
                }
                String currentValue = current.get(binding.id);
                if (currentValue == null) {
                    diff.changedKeybinds.add(binding.id + ": <missing> -> " + value);
                } else if (!currentValue.equals(value)) {
                    diff.changedKeybinds.add(binding.id + ": " + currentValue + " -> " + value);
                }
            }
        }

        if (reader.exists(UniversalConfigFormat.PROFILE_OPTIONS_ENTRY)) {
            OptionsFragmentsDocument fragments = JsonDocuments.read(reader, UniversalConfigFormat.PROFILE_OPTIONS_ENTRY, OptionsFragmentsDocument.class);
            Map<String, String> currentOptions = currentOptionValues(optionsPath(instancePath), false);
            for (OptionsFragmentsDocument.OptionEntry option : optionEntries(fragments)) {
                if (!isValidClientOption(option)) {
                    diff.skippedItems.add("Malformed client option skipped");
                    continue;
                }
                String currentValue = currentOptions.get(option.key);
                if (currentValue == null) {
                    diff.changedKeybinds.add(option.key + ": <missing> -> " + option.value);
                } else if (!currentValue.equals(option.value)) {
                    diff.changedKeybinds.add(option.key + ": " + currentValue + " -> " + option.value);
                }
            }
        }

        for (String entry : reader.entries()) {
            if (!UniversalConfigFormat.isProfileConfigEntry(entry)) {
                continue;
            }
            String relative = UniversalConfigFormat.profileConfigRelativePath(entry);
            if (isUniversalConfigInternalPath(relative)) {
                diff.skippedItems.add(configDisplayPath(relative) + " (Universal Config internal file)");
                continue;
            }
            Path target = safeConfigPath(instancePath, relative);
            if (Files.exists(target)) {
                diff.replacedFiles.add(configDisplayPath(relative));
                diff.raiseRisk(RiskLevel.MEDIUM);
            } else {
                diff.addedFiles.add(configDisplayPath(relative));
            }
        }

        if (!diff.checksumWarnings.isEmpty()) {
            diff.raiseRisk(RiskLevel.HIGH);
        }
        return diff;
    }

    @Override
    public Path backup(Path instancePath, UniversalConfigSettings settings, MinecraftEnvironment environment) throws UniversalConfigException {
        BackupManifest manifest = new BackupManifest();
        manifest.createdAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        manifest.instancePath = instancePath.toAbsolutePath().normalize().toString();
        manifest.minecraftVersion = environment.minecraftVersion();
        manifest.loader = environment.loaderId();

        String timestamp = manifest.createdAt.replace(":", "").replace("+", "-").replace(".", "-");
        Path backupPath = UniversalConfigPaths.backupsDirectory(settings)
                .resolve(UniversalConfigFormat.BACKUP_FILENAME_PREFIX + timestamp + UniversalConfigFormat.BACKUP_FILE_EXTENSION);
        try {
            Files.createDirectories(backupPath.getParent());
            FileOperationLogger.info("CREATE_DIRECTORY", backupPath.getParent(), "backup parent");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(backupPath))) {
                Path options = optionsPath(instancePath);
                if (Files.exists(options)) {
                    addFileToBackup(output, options, UniversalConfigFormat.BACKUP_ORIGINAL_DIRECTORY + UniversalConfigFormat.OPTIONS_FILE_NAME);
                    manifest.files.add(UniversalConfigFormat.OPTIONS_FILE_NAME);
                }
                Path config = configPath(instancePath);
                if (Files.isDirectory(config)) {
                    try (Stream<Path> stream = Files.walk(config)) {
                        for (Path source : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                            String relative = config.relativize(source).toString().replace('\\', '/');
                            if (isUniversalConfigInternalPath(relative)) {
                                FileOperationLogger.info("SKIP_INTERNAL_CONFIG_BACKUP", source, relative);
                                continue;
                            }
                            addFileToBackup(output, source, UniversalConfigFormat.backupConfigEntry(relative));
                            manifest.files.add(configDisplayPath(relative));
                        }
                    }
                }
                output.putNextEntry(new ZipEntry(UniversalConfigFormat.BACKUP_MANIFEST_ENTRY));
                output.write(JsonDocuments.toJson(manifest).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
                FileOperationLogger.info("WRITE_ZIP_ENTRY", backupPath, UniversalConfigFormat.BACKUP_MANIFEST_ENTRY);
            }
            FileOperationLogger.info("CREATE_BACKUP", backupPath, "files=" + manifest.files.size());
            return backupPath;
        } catch (IOException ex) {
            FileOperationLogger.failure("CREATE_BACKUP", backupPath, "failed", ex);
            throw new UniversalConfigException("Failed to create backup.", ex);
        }
    }

    @Override
    public void restore(Path instancePath, ProfileArchiveReader reader) throws UniversalConfigException {
        for (String entry : reader.entries()) {
            if (!UniversalConfigFormat.isBackupOriginalEntry(entry)) {
                continue;
            }
            String relative = UniversalConfigFormat.backupOriginalRelativePath(entry);
            Path target = ZipSecurity.safeResolve(instancePath, relative);
            try {
                Files.createDirectories(target.getParent());
                FileOperationLogger.info("CREATE_DIRECTORY", target.getParent(), "restore parent");
                try (InputStream input = reader.open(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    FileOperationLogger.info("RESTORE_FILE", target, entry);
                }
            } catch (IOException ex) {
                FileOperationLogger.failure("RESTORE_FILE", target, entry, ex);
                throw new UniversalConfigException("Failed to restore backup entry: " + relative, ex);
            }
        }
    }

    public KeybindsDocument extractKeybinds(Path optionsPath) throws UniversalConfigException {
        KeybindsDocument document = new KeybindsDocument();
        if (!Files.exists(optionsPath)) {
            FileOperationLogger.info("READ_OPTIONS", optionsPath, "missing");
            return document;
        }
        try {
            FileOperationLogger.info("READ_OPTIONS", optionsPath, "extract keybinds");
            for (String line : Files.readAllLines(optionsPath, StandardCharsets.UTF_8)) {
                if (!line.startsWith("key_")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 1) {
                    continue;
                }
                KeybindsDocument.KeyBindingEntry entry = new KeybindsDocument.KeyBindingEntry();
                entry.id = line.substring(0, colon);
                entry.displayName = displayNameFromKeyId(entry.id);
                String value = line.substring(colon + 1);
                Integer legacy = parseInteger(value);
                if (legacy == null) {
                    entry.modernValue = value;
                } else {
                    entry.legacyValue = legacy;
                }
                document.bindings.add(entry);
            }
            return document;
        } catch (IOException ex) {
            FileOperationLogger.failure("READ_OPTIONS", optionsPath, "extract keybinds", ex);
            throw new UniversalConfigException("Failed to extract keybinds from options.txt.", ex);
        }
    }

    public OptionsFragmentsDocument extractOptionsFragments(Path optionsPath) throws UniversalConfigException {
        OptionsFragmentsDocument document = new OptionsFragmentsDocument();
        Map<String, String> options = currentOptionValues(optionsPath, true);
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (!MinecraftConfigPolicy.isValidClientOption(entry.getKey(), entry.getValue())) {
                continue;
            }
            OptionsFragmentsDocument.OptionEntry option = new OptionsFragmentsDocument.OptionEntry();
            option.key = entry.getKey();
            option.value = entry.getValue();
            document.options.add(option);
        }
        return document;
    }

    private void applyKeybinds(Path optionsPath, KeybindsDocument keybinds, ProfileDiff diff) throws UniversalConfigException {
        try {
            List<String> lines = Files.exists(optionsPath)
                    ? Files.readAllLines(optionsPath, StandardCharsets.UTF_8)
                    : new ArrayList<>();
            Map<String, KeybindsDocument.KeyBindingEntry> requested = new LinkedHashMap<>();
            for (KeybindsDocument.KeyBindingEntry binding : keybinds.bindings) {
                requested.put(binding.id, binding);
            }

            Map<String, String> current = currentKeybindValues(optionsPath);
            boolean modernOptions = usesModernKeybindValues(current, optionsPath);
            Set<String> applied = new HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int colon = line.indexOf(':');
                if (colon < 1 || !line.startsWith("key_")) {
                    continue;
                }
                String id = line.substring(0, colon);
                KeybindsDocument.KeyBindingEntry binding = requested.get(id);
                if (binding == null) {
                    continue;
                }
                String value = binding.valueForCurrentOptions(modernOptions);
                if (value == null) {
                    diff.skippedItems.add(id + " (unsupported key format)");
                    continue;
                }
                lines.set(i, id + ":" + value);
                applied.add(id);
            }

            for (KeybindsDocument.KeyBindingEntry binding : keybinds.bindings) {
                if (!applied.contains(binding.id)) {
                    String value = binding.valueForCurrentOptions(modernOptions);
                    if (value == null) {
                        diff.skippedItems.add(binding.id + " (unsupported key format)");
                    } else {
                        lines.add(binding.id + ":" + value);
                    }
                }
            }

            Files.write(optionsPath, lines, StandardCharsets.UTF_8);
            FileOperationLogger.info("WRITE_OPTIONS", optionsPath, "applied keybinds=" + applied.size());
        } catch (IOException ex) {
            FileOperationLogger.failure("WRITE_OPTIONS", optionsPath, "apply keybinds", ex);
            throw new UniversalConfigException("Failed to apply keybinds.", ex);
        }
    }

    private void applyOptionsFragments(Path optionsPath, OptionsFragmentsDocument fragments, ProfileDiff diff) throws UniversalConfigException {
        try {
            List<String> lines = Files.exists(optionsPath)
                    ? Files.readAllLines(optionsPath, StandardCharsets.UTF_8)
                    : new ArrayList<>();
            Map<String, OptionsFragmentsDocument.OptionEntry> requested = new LinkedHashMap<>();
            for (OptionsFragmentsDocument.OptionEntry option : optionEntries(fragments)) {
                if (isValidClientOption(option)) {
                    requested.put(option.key, option);
                } else {
                    diff.skippedItems.add("Malformed client option skipped");
                    FileOperationLogger.info("SKIP_CLIENT_OPTION", optionsPath, "malformed entry");
                }
            }

            Set<String> applied = new HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int colon = line.indexOf(':');
                if (colon < 1 || line.startsWith("key_")) {
                    continue;
                }
                String key = line.substring(0, colon);
                OptionsFragmentsDocument.OptionEntry option = requested.get(key);
                if (option == null) {
                    continue;
                }
                lines.set(i, key + ":" + option.value);
                applied.add(key);
            }

            for (OptionsFragmentsDocument.OptionEntry option : requested.values()) {
                if (!applied.contains(option.key)) {
                    lines.add(option.key + ":" + option.value);
                }
            }

            Files.write(optionsPath, lines, StandardCharsets.UTF_8);
            FileOperationLogger.info("WRITE_OPTIONS", optionsPath, "applied clientOptions=" + requested.size());
        } catch (IOException ex) {
            FileOperationLogger.failure("WRITE_OPTIONS", optionsPath, "apply client options", ex);
            throw new UniversalConfigException("Failed to apply client options.", ex);
        }
    }

    private Map<String, String> currentKeybindValues(Path optionsPath) throws UniversalConfigException {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(optionsPath)) {
            FileOperationLogger.info("READ_OPTIONS", optionsPath, "missing current keybinds");
            return values;
        }
        try {
            FileOperationLogger.info("READ_OPTIONS", optionsPath, "current keybinds");
            for (String line : Files.readAllLines(optionsPath, StandardCharsets.UTF_8)) {
                if (line.startsWith("key_")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        values.put(line.substring(0, colon), line.substring(colon + 1));
                    }
                }
            }
            return values;
        } catch (IOException ex) {
            FileOperationLogger.failure("READ_OPTIONS", optionsPath, "current keybinds", ex);
            throw new UniversalConfigException("Failed to read current keybinds.", ex);
        }
    }

    private Map<String, String> currentOptionValues(Path optionsPath, boolean logMissing) throws UniversalConfigException {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(optionsPath)) {
            if (logMissing) {
                FileOperationLogger.info("READ_OPTIONS", optionsPath, "missing client options");
            }
            return values;
        }
        try {
            FileOperationLogger.info("READ_OPTIONS", optionsPath, "client options");
            for (String line : Files.readAllLines(optionsPath, StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon > 0 && !line.startsWith("key_")) {
                    values.put(line.substring(0, colon), line.substring(colon + 1));
                }
            }
            return values;
        } catch (IOException ex) {
            FileOperationLogger.failure("READ_OPTIONS", optionsPath, "client options", ex);
            throw new UniversalConfigException("Failed to read client options.", ex);
        }
    }

    private boolean usesModernKeybindValues(Map<String, String> values, Path optionsPath)
            throws UniversalConfigException {
        if (values.values().stream().anyMatch(value -> value.startsWith("key."))) {
            return true;
        }
        if (values.values().stream().anyMatch(value -> parseInteger(value) != null)) {
            return false;
        }
        // During Forge's first-start pre-initialization, options.txt already contains the modern DataVersion-style
        // version field but Minecraft may not have written its default key lines yet. Treating an empty key map as
        // legacy makes dual-format profiles write values such as "2", which 1.13+ rejects and can make the automatic
        // restart look unsuccessful. Legacy options files do not contain this numeric version field.
        return currentOptionValues(optionsPath, false).containsKey("version");
    }

    private List<String> selectConfigFiles(Path instancePath, ProfileCreateOptions options) throws UniversalConfigException {
        Path configRoot = configPath(instancePath);
        if (!Files.isDirectory(configRoot)) {
            FileOperationLogger.info("LIST_CONFIG", configRoot, "missing");
            return Collections.emptyList();
        }
        if (!options.configRelativePaths.isEmpty()) {
            List<String> selected = new ArrayList<>();
            for (String relative : options.configRelativePaths) {
                if (isUniversalConfigInternalPath(relative)) {
                    FileOperationLogger.info("SKIP_INTERNAL_CONFIG_EXPORT", configRoot.resolve(relative), relative);
                    continue;
                }
                Path path = safeConfigPath(instancePath, relative);
                if (Files.isRegularFile(path)) {
                    selected.add(relative.replace('\\', '/'));
                }
            }
            return selected;
        }
        try (Stream<Path> stream = Files.walk(configRoot)) {
            FileOperationLogger.info("LIST_CONFIG", configRoot, "walk");
            return stream.filter(Files::isRegularFile)
                    .map(configRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(this::allowedConfigPath)
                    .filter(path -> !isUniversalConfigInternalPath(path))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            FileOperationLogger.failure("LIST_CONFIG", configRoot, "walk", ex);
            throw new UniversalConfigException("Failed to list config files.", ex);
        }
    }

    private boolean allowedConfigPath(String relative) {
        return MinecraftConfigPolicy.isAllowedConfigFile(relative);
    }

    private List<OptionsFragmentsDocument.OptionEntry> optionEntries(OptionsFragmentsDocument document)
            throws UniversalConfigException {
        if (document == null || document.options == null) {
            throw new UniversalConfigException("Profile client options are invalid.");
        }
        return document.options;
    }

    private boolean isValidClientOption(OptionsFragmentsDocument.OptionEntry option) {
        return option != null && MinecraftConfigPolicy.isValidClientOption(option.key, option.value);
    }

    private boolean isUniversalConfigInternalPath(String relative) {
        String normalized = relative.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.equals(UniversalConfigFormat.LOCAL_SETTINGS_FILE_NAME)
                || normalized.equals(UniversalConfigFormat.PENDING_IMPORT_FILE_NAME)
                || normalized.startsWith(UniversalConfigFormat.LEGACY_INTERNAL_DIRECTORY_PREFIX)
                || normalized.startsWith(UniversalConfigFormat.INTERNAL_DIRECTORY_PREFIX);
    }

    private Path safeConfigPath(Path instancePath, String relative) throws UniversalConfigException {
        String normalized = relative.replace('\\', '/');
        if (normalized.contains("/")) {
            String top = normalized.substring(0, normalized.indexOf('/')).toLowerCase(Locale.ROOT);
            if (MinecraftConfigPolicy.isDeniedConfigTopLevel(top)) {
                throw new UniversalConfigException("Denied config path: " + relative);
            }
        }
        return ZipSecurity.safeResolve(configPath(instancePath), normalized);
    }

    private void verifyChecksums(ProfileArchiveReader reader, ProfileDiff diff) throws UniversalConfigException {
        if (!reader.exists(UniversalConfigFormat.CHECKSUMS_ENTRY)) {
            diff.checksumWarnings.add(UniversalConfigFormat.CHECKSUMS_ENTRY + " is missing.");
            return;
        }
        ChecksumDocument checksums = JsonDocuments.read(reader, UniversalConfigFormat.CHECKSUMS_ENTRY, ChecksumDocument.class);
        for (Map.Entry<String, String> expected : checksums.files.entrySet()) {
            try (InputStream input = reader.open(expected.getKey())) {
                String actual = Checksums.sha256(input);
                if (!actual.equalsIgnoreCase(expected.getValue())) {
                    diff.checksumWarnings.add(expected.getKey() + " checksum mismatch.");
                }
            } catch (IOException ex) {
                throw new UniversalConfigException("Failed to verify checksum: " + expected.getKey(), ex);
            }
        }
    }

    private void addCompatibilityWarnings(ProfileDiff diff, ProfileManifest manifest, MinecraftEnvironment environment) {
        if (manifest.source == null) {
            diff.warnings.add("Profile manifest source is missing.");
            diff.raiseRisk(RiskLevel.MEDIUM);
            return;
        }
        if (!same(manifest.source.minecraftVersion, environment.minecraftVersion())) {
            diff.warnings.add("Minecraft version differs: profile " + manifest.source.minecraftVersion
                    + " / current " + environment.minecraftVersion());
            diff.raiseRisk(RiskLevel.MEDIUM);
            if (majorVersionDistance(manifest.source.minecraftVersion, environment.minecraftVersion()) >= 3) {
                diff.warnings.add("Minecraft versions are far apart; key formats may be incompatible.");
                diff.raiseRisk(RiskLevel.HIGH);
            }
        }
        if (!same(manifest.source.loader, environment.loaderId())) {
            diff.warnings.add("Mod Loader differs: profile " + manifest.source.loader + " / current " + environment.loaderId());
            diff.raiseRisk(RiskLevel.HIGH);
        }
        if (!same(manifest.source.loaderVersion, environment.loaderVersion())) {
            diff.warnings.add("Loader version differs: profile " + manifest.source.loaderVersion
                    + " / current " + environment.loaderVersion());
            diff.raiseRisk(RiskLevel.MEDIUM);
        }
        if (manifest.compatibility == null
                || manifest.compatibility.testedVersions == null
                || !manifest.compatibility.testedVersions.contains(environment.minecraftVersion())) {
            diff.warnings.add("Current Minecraft version is not listed in testedVersions.");
            diff.raiseRisk(RiskLevel.MEDIUM);
        }
    }

    private int majorVersionDistance(String left, String right) {
        Integer leftMinor = minecraftMinor(left);
        Integer rightMinor = minecraftMinor(right);
        if (leftMinor == null || rightMinor == null) {
            return 0;
        }
        return Math.abs(leftMinor - rightMinor);
    }

    private Integer minecraftMinor(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        return parseInteger(parts[1]);
    }

    private boolean same(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private void addFileToBackup(ZipOutputStream output, Path source, String entryName) throws IOException, UniversalConfigException {
        ZipSecurity.validateRelativeEntryName(entryName);
        output.putNextEntry(new ZipEntry(entryName));
        Files.copy(source, output);
        output.closeEntry();
        FileOperationLogger.info("WRITE_ZIP_ENTRY", null, "backup " + entryName + " from " + source.toAbsolutePath().normalize());
    }

    private void writerAddJson(ProfileArchiveWriter writer, String entryName, Object value) throws UniversalConfigException {
        try {
            writer.addString(entryName, JsonDocuments.toJson(value));
        } catch (IOException ex) {
            throw new UniversalConfigException("Failed to write profile JSON: " + entryName, ex);
        }
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String displayNameFromKeyId(String id) {
        return id.replace("key_key.", "").replace("key_", "").replace('.', ' ');
    }

    private Path optionsPath(Path instancePath) {
        return UniversalConfigPaths.optionsFile(instancePath);
    }

    private Path configPath(Path instancePath) {
        return UniversalConfigPaths.configDirectory(instancePath);
    }

    private String configDisplayPath(String relativePath) {
        return UniversalConfigFormat.CONFIG_DIRECTORY_NAME + "/" + relativePath.replace('\\', '/');
    }
}
