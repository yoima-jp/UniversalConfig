package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProfileStartupTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsMinecraftAccessibilityOnboardingAsFirstStart() throws Exception {
        UniversalConfigSettings settings = new UniversalConfigSettings(tempDir.resolve("shared"));
        ProfileService service = new ProfileService(settings);
        Path instance = tempDir.resolve("instance");

        assertTrue(service.isFirstMinecraftStart(instance));

        Files.createDirectories(instance);
        Files.writeString(UniversalConfigPaths.optionsFile(instance),
                "onboardAccessibility:true\nguiScale:2\n", StandardCharsets.UTF_8);
        assertTrue(service.isFirstMinecraftStart(instance));

        Files.writeString(UniversalConfigPaths.optionsFile(instance),
                "onboardAccessibility:false\nguiScale:2\n", StandardCharsets.UTF_8);
        assertFalse(service.isFirstMinecraftStart(instance));
    }

    @Test
    void sharesDefaultPathWithAnotherInstance() throws Exception {
        Path sharedRoot = tempDir.resolve("shared");
        Path profile = sharedRoot.resolve(UniversalConfigFormat.PROFILES_DIRECTORY_NAME).resolve("main.ucp");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "placeholder", StandardCharsets.UTF_8);

        UniversalConfigSettings firstSettings = new UniversalConfigSettings(sharedRoot);
        firstSettings.setDefaultProfilePath(profile);
        UniversalConfigPaths.saveSettings(tempDir.resolve("first-instance"), firstSettings);

        Path secondInstance = tempDir.resolve("second-instance");
        Path secondLocalSettings = UniversalConfigPaths.localSettingsFile(secondInstance);
        Files.createDirectories(secondLocalSettings.getParent());
        Files.writeString(secondLocalSettings,
                "{\"rootDirectory\":\"" + escapeJson(sharedRoot.toAbsolutePath().normalize().toString()) + "\"}",
                StandardCharsets.UTF_8);

        UniversalConfigSettings loaded = UniversalConfigPaths.loadOrCreateSettings(secondInstance);

        assertEquals(profile.toAbsolutePath().normalize(), loaded.defaultProfilePath());
    }

    @Test
    void appliesDefaultOnceAndWritesMarkerOnlyAfterSuccess() throws Exception {
        Path sharedRoot = tempDir.resolve("shared");
        Path source = tempDir.resolve("source-instance");
        Path target = tempDir.resolve("new-instance");
        Files.createDirectories(source);
        Files.createDirectories(target);
        Files.writeString(UniversalConfigPaths.optionsFile(source),
                "onboardAccessibility:false\nguiScale:3\n", StandardCharsets.UTF_8);

        UniversalConfigSettings settings = new UniversalConfigSettings(sharedRoot);
        UniversalConfigPaths.ensureDirectories(settings);
        ProfileService service = new ProfileService(settings);
        MinecraftEnvironment sourceEnvironment = new MinecraftEnvironment(
                source, "1.20.1", ModLoader.FABRIC, "0.16.14");
        ProfileCreateOptions options = new ProfileCreateOptions();
        options.name = "Default";
        options.includeKeybinds = false;
        options.includeClientOptions = true;
        options.includeModConfigs = false;
        Path profile = service.createProfile(source, options, sourceEnvironment);
        service.setDefaultProfile(source, profile);

        MinecraftEnvironment targetEnvironment = new MinecraftEnvironment(
                target, "1.20.1", ModLoader.FABRIC, "0.16.14");
        ProfileService.ApplyResult firstResult = service.applyDefaultProfileOnFirstStart(target, targetEnvironment);

        assertNotNull(firstResult);
        assertTrue(Files.isRegularFile(UniversalConfigPaths.defaultProfileAppliedMarker(target)));
        assertTrue(Files.readString(UniversalConfigPaths.optionsFile(target), StandardCharsets.UTF_8)
                .contains("guiScale:3"));
        assertNull(service.applyDefaultProfileOnFirstStart(target, targetEnvironment));
    }

    @Test
    void manualPendingImportTakesPriorityOverDefault() throws Exception {
        Path instance = tempDir.resolve("instance");
        Files.createDirectories(instance);
        UniversalConfigSettings settings = new UniversalConfigSettings(tempDir.resolve("shared"));
        ProfileService service = new ProfileService(settings);
        PendingImport pending = new PendingImport();
        pending.profilePath = tempDir.resolve("manual.ucp").toString();
        JsonDocuments.write(UniversalConfigPaths.pendingImportFile(instance), pending);

        ProfileService.ApplyResult result = service.applyDefaultProfileOnFirstStart(instance,
                new MinecraftEnvironment(instance, "1.20.1", ModLoader.FABRIC, "0.16.14"));

        assertNull(result);
        assertFalse(Files.exists(UniversalConfigPaths.defaultProfileAppliedMarker(instance)));
    }

    @Test
    void failedApplyDoesNotWriteMarker() throws Exception {
        Path sharedRoot = tempDir.resolve("shared");
        Path source = tempDir.resolve("source");
        Files.createDirectories(source);
        Files.writeString(UniversalConfigPaths.optionsFile(source), "guiScale:3\n", StandardCharsets.UTF_8);
        UniversalConfigSettings settings = new UniversalConfigSettings(sharedRoot);
        UniversalConfigPaths.ensureDirectories(settings);
        ProfileService service = new ProfileService(settings);
        MinecraftEnvironment environment = new MinecraftEnvironment(
                source, "1.20.1", ModLoader.FABRIC, "0.16.14");
        ProfileCreateOptions options = new ProfileCreateOptions();
        options.name = "Default";
        options.includeKeybinds = false;
        options.includeClientOptions = true;
        options.includeModConfigs = false;
        Path profile = service.createProfile(source, options, environment);
        service.setDefaultProfile(source, profile);

        Path invalidInstance = tempDir.resolve("not-a-directory");
        Files.writeString(invalidInstance, "file", StandardCharsets.UTF_8);

        assertThrows(UniversalConfigException.class, () -> service.applyDefaultProfileOnFirstStart(
                invalidInstance,
                new MinecraftEnvironment(invalidInstance, "1.20.1", ModLoader.FABRIC, "0.16.14")));
        assertFalse(Files.exists(UniversalConfigPaths.defaultProfileAppliedMarker(invalidInstance)));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
