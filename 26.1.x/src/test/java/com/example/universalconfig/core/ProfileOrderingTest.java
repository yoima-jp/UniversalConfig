package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileOrderingTest {
    @TempDir
    Path tempDir;

    @Test
    void renamesProfileWithoutChangingItsArchiveIdentity() throws Exception {
        Path instance = createInstance();
        ProfileService service = new ProfileService(new UniversalConfigSettings(tempDir.resolve("shared")));
        Path profile = service.createProfile(instance, options("Original"), environment(instance));

        service.renameProfile(profile, "Renamed");

        assertEquals("Renamed", service.readManifest(profile).name);
        assertThrows(UniversalConfigException.class, () -> service.renameProfile(profile, "  "));
    }

    @Test
    void movesProfilesAndPersistsTheOrderInSharedSettings() throws Exception {
        Path instance = createInstance();
        UniversalConfigSettings settings = new UniversalConfigSettings(tempDir.resolve("shared"));
        ProfileService service = new ProfileService(settings);
        Path first = service.createProfile(instance, options("First"), environment(instance));
        service.createProfile(instance, options("Second"), environment(instance));
        service.createProfile(instance, options("Third"), environment(instance));

        int initialIndex = service.listProfiles().stream().map(ProfileSummary::path).toList().indexOf(first);
        int targetIndex = initialIndex == 0 ? 2 : 0;
        service.moveProfile(instance, first, targetIndex);

        List<ProfileSummary> ordered = service.listProfiles();
        assertEquals(first, ordered.get(targetIndex).path());

        UniversalConfigSettings reloaded = UniversalConfigPaths.loadOrCreateSettings(instance);
        List<ProfileSummary> reloadedProfiles = new ProfileService(reloaded).listProfiles();
        assertEquals(first, reloadedProfiles.get(targetIndex).path());
    }

    // PR #43 P1 回帰: 複製プロファイルが元の manifest.id を保持したまま並べ替えられると、
    // id ベースの重複排除で片方が一覧から消えていた。ファイル名ベースの並び順キーで解消する。
    @Test
    void duplicateSurvivesReorderAndReload() throws Exception {
        Path instance = createInstance();
        UniversalConfigSettings settings = new UniversalConfigSettings(tempDir.resolve("shared"));
        ProfileService service = new ProfileService(settings);
        Path original = service.createProfile(instance, options("Original"), environment(instance));
        service.createProfile(instance, options("Other"), environment(instance));

        Path duplicate = service.duplicateProfile(original);
        assertNotEquals(original, duplicate);

        // 複製を先頭へ移動し、設定を保存する。
        int duplicateIndex = service.listProfiles().stream().map(ProfileSummary::path).toList().indexOf(duplicate);
        service.moveProfile(instance, duplicate, 0);

        // 設定を再読み込みしても、元と複製の両方が一覧に残ること。
        UniversalConfigSettings reloaded = UniversalConfigPaths.loadOrCreateSettings(instance);
        List<Path> reloadedPaths = new ProfileService(reloaded).listProfiles().stream()
                .map(ProfileSummary::path).toList();
        assertTrue(reloadedPaths.contains(original), "original profile must remain after reorder + reload");
        assertTrue(reloadedPaths.contains(duplicate), "duplicate profile must remain after reorder + reload");
        assertEquals(duplicate, reloadedPaths.get(0), "duplicate must stay at the moved position after reload");
    }

    private Path createInstance() throws Exception {
        Path instance = tempDir.resolve("instance");
        Files.createDirectories(instance);
        Files.writeString(UniversalConfigPaths.optionsFile(instance), "guiScale:3\n", StandardCharsets.UTF_8);
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
        return new MinecraftEnvironment(instance, "1.20.1", ModLoader.FABRIC, "0.16.14");
    }
}
