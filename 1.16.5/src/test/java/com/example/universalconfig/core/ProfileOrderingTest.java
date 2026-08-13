package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileOrderingTest {
    @TempDir
    Path tempDir;

    @Test
    void renamesAndPersistsProfileOrder() throws Exception {
        Path instance = createInstance();
        ProfileService service = new ProfileService(new UniversalConfigSettings(tempDir.resolve("shared")));
        Path first = service.createProfile(instance, options("First"), environment(instance));
        service.createProfile(instance, options("Second"), environment(instance));

        service.renameProfile(first, "Renamed");
        assertEquals("Renamed", service.readManifest(first).name);
        assertThrows(UniversalConfigException.class, () -> service.renameProfile(first, "  "));

        List<Path> before = service.listProfiles().stream()
                .map(ProfileSummary::path).collect(Collectors.toList());
        int targetIndex = before.indexOf(first) == 0 ? 1 : 0;
        service.moveProfile(instance, first, targetIndex);
        UniversalConfigSettings reloaded = UniversalConfigPaths.loadOrCreateSettings(instance);
        assertEquals(first, new ProfileService(reloaded).listProfiles().get(targetIndex).path());
    }

    @Test
    void duplicateSurvivesReorderAndReload() throws Exception {
        Path instance = createInstance();
        ProfileService service = new ProfileService(new UniversalConfigSettings(tempDir.resolve("shared")));
        Path original = service.createProfile(instance, options("Original"), environment(instance));
        service.createProfile(instance, options("Other"), environment(instance));
        Path duplicate = service.duplicateProfile(original);
        assertNotEquals(original, duplicate);

        service.moveProfile(instance, duplicate, 0);
        UniversalConfigSettings reloaded = UniversalConfigPaths.loadOrCreateSettings(instance);
        List<Path> paths = new ProfileService(reloaded).listProfiles().stream()
                .map(ProfileSummary::path).collect(Collectors.toList());
        assertTrue(paths.contains(original));
        assertTrue(paths.contains(duplicate));
        assertEquals(duplicate, paths.get(0));
    }

    private Path createInstance() throws Exception {
        Path instance = tempDir.resolve("instance");
        Files.createDirectories(instance);
        Files.write(UniversalConfigPaths.optionsFile(instance),
                "guiScale:3\n".getBytes(StandardCharsets.UTF_8));
        return instance;
    }

    private ProfileCreateOptions options(String name) {
        ProfileCreateOptions options = new ProfileCreateOptions();
        options.name = name;
        options.includeKeybinds = false;
        options.includeClientOptions = true;
        options.includeModConfigs = false;
        return options;
    }

    private MinecraftEnvironment environment(Path instance) {
        return new MinecraftEnvironment(instance, "1.16.5", ModLoader.FORGE, "36.2.42");
    }
}
