package com.example.universalconfig.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileImportTest {
    @TempDir
    Path tempDir;

    @Test
    void copiesValidatedProfileIntoSharedFolderWithUniqueName() throws Exception {
        Path sourceProfile = createExternalProfile();
        UniversalConfigSettings targetSettings = new UniversalConfigSettings(tempDir.resolve("target-shared"));
        ProfileService targetService = new ProfileService(targetSettings);

        Path first = targetService.importProfile(sourceProfile);
        Path second = targetService.importProfile(sourceProfile);
        Path third = targetService.importProfile(sourceProfile);

        Path profilesDirectory = UniversalConfigPaths.profilesDirectory(targetSettings).toAbsolutePath().normalize();
        assertTrue(first.toAbsolutePath().normalize().startsWith(profilesDirectory));
        assertTrue(second.toAbsolutePath().normalize().startsWith(profilesDirectory));
        assertNotEquals(first, second);
        assertArrayEquals(Files.readAllBytes(sourceProfile), Files.readAllBytes(first));
        assertEquals("Imported Profile", targetService.readManifest(first).name);
        assertEquals("Imported Profile (1)", targetService.readManifest(second).name);
        assertEquals("Imported Profile (2)", targetService.readManifest(third).name);
        assertEquals(3, targetService.listProfiles().size());

        targetService.deleteProfile(second);

        assertEquals("Imported Profile", targetService.readManifest(first).name);
        assertEquals("Imported Profile (2)", targetService.readManifest(third).name);
    }

    @Test
    void usesSafeFallbackWhenManifestIdIsMissing() throws Exception {
        Path sourceProfile = tempDir.resolve("missing-id.ucp");
        ProfileManifest manifest = new ProfileManifest();
        manifest.id = null;
        manifest.name = "Missing ID";
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(sourceProfile))) {
            output.putNextEntry(new ZipEntry(UniversalConfigFormat.MANIFEST_ENTRY));
            output.write(JsonDocuments.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        ProfileService service = new ProfileService(
                new UniversalConfigSettings(tempDir.resolve("fallback-shared")));

        Path imported = service.importProfile(sourceProfile);

        assertEquals(UniversalConfigFormat.DEFAULT_PROFILE_SLUG + UniversalConfigFormat.PROFILE_FILE_EXTENSION,
                imported.getFileName().toString());
    }

    @Test
    void preservesUnknownManifestFieldsAndRebuildsChecksumsWhenNameChanges() throws Exception {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", UniversalConfigFormat.PROFILE_FORMAT);
        manifest.addProperty("formatVersion", UniversalConfigFormat.FORMAT_VERSION);
        manifest.addProperty("id", "future-profile");
        manifest.addProperty("name", "Future Profile");
        manifest.addProperty("futureField", "preserved");
        byte[] manifestBytes = JsonDocuments.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        Path sourceProfile = tempDir.resolve("future-profile.ucp");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(sourceProfile))) {
            output.putNextEntry(new ZipEntry(UniversalConfigFormat.MANIFEST_ENTRY));
            output.write(manifestBytes);
            output.closeEntry();
        }

        ProfileService service = new ProfileService(
                new UniversalConfigSettings(tempDir.resolve("future-shared")));
        service.importProfile(sourceProfile);
        Path second = service.importProfile(sourceProfile);

        try (ZipArchiveReader reader = new ZipArchiveReader(second);
             InputStream manifestInput = reader.open(UniversalConfigFormat.MANIFEST_ENTRY)) {
            JsonObject importedManifest = JsonDocuments.GSON.fromJson(
                    new String(manifestInput.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
            assertEquals("Future Profile (1)", importedManifest.get("name").getAsString());
            assertEquals("preserved", importedManifest.get("futureField").getAsString());

            ChecksumDocument checksums = JsonDocuments.read(reader, UniversalConfigFormat.CHECKSUMS_ENTRY,
                    ChecksumDocument.class);
            for (Map.Entry<String, String> expected : checksums.files.entrySet()) {
                try (InputStream entry = reader.open(expected.getKey())) {
                    assertEquals(expected.getValue(), Checksums.sha256(entry.readAllBytes()));
                }
            }
        }
    }

    @Test
    void rejectsMissingWrongExtensionAndBrokenArchives() throws Exception {
        ProfileService service = new ProfileService(
                new UniversalConfigSettings(tempDir.resolve("invalid-shared")));
        Path wrongExtension = tempDir.resolve("profile.zip");
        Path brokenProfile = tempDir.resolve("broken.ucp");
        Files.writeString(wrongExtension, "not a profile", StandardCharsets.UTF_8);
        Files.writeString(brokenProfile, "not a zip", StandardCharsets.UTF_8);

        assertThrows(UniversalConfigException.class, () -> service.importProfile(null));
        assertThrows(UniversalConfigException.class, () -> service.importProfile(wrongExtension));
        assertThrows(UniversalConfigException.class, () -> service.importProfile(brokenProfile));
    }

    private Path createExternalProfile() throws Exception {
        Path sourceInstance = tempDir.resolve("source-instance");
        Files.createDirectories(sourceInstance);
        Files.writeString(UniversalConfigPaths.optionsFile(sourceInstance), "guiScale:3\n", StandardCharsets.UTF_8);
        ProfileService sourceService = new ProfileService(
                new UniversalConfigSettings(tempDir.resolve("source-shared")));
        ProfileCreateOptions options = new ProfileCreateOptions();
        options.name = "Imported Profile";
        options.includeKeybinds = false;
        options.includeClientOptions = true;
        options.includeModConfigs = false;
        return sourceService.createProfile(sourceInstance, options,
                new MinecraftEnvironment(sourceInstance, "1.20.1", ModLoader.FABRIC, "0.16.14"));
    }
}
