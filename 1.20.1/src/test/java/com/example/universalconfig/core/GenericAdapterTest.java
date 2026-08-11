package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericAdapterTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsOnlyKeybindLinesFromOptions() throws Exception {
        Path options = tempDir.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
        Files.writeString(options, String.join("\n",
                "lang:ja_jp",
                "key_key.forward:key.keyboard.w",
                "fov:0.0",
                "key_key.jump:key.keyboard.space"
        ), StandardCharsets.UTF_8);

        KeybindsDocument document = new GenericAdapter().extractKeybinds(options);

        assertEquals(2, document.bindings.size());
        assertEquals("key_key.forward", document.bindings.get(0).id);
        assertEquals("key.keyboard.w", document.bindings.get(0).modernValue);
        assertEquals("key_key.jump", document.bindings.get(1).id);
    }

    @Test
    void extractsAllClientOptionsExceptKeybinds() throws Exception {
        Path options = tempDir.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
        Files.writeString(options, String.join("\n",
                "lang:ja_jp",
                "soundCategory_master:0.25",
                "soundCategory_music:0.0",
                "guiScale:3",
                "key_key.forward:key.keyboard.w",
                "lastServer:example.invalid:25565",
                "graphicsMode:1"
        ), StandardCharsets.UTF_8);

        OptionsFragmentsDocument document = new GenericAdapter().extractOptionsFragments(options);

        assertEquals(6, document.options.size());
        assertEquals("lang", document.options.get(0).key);
        assertEquals("ja_jp", document.options.get(0).value);
        assertEquals("soundCategory_master", document.options.get(1).key);
        assertEquals("0.25", document.options.get(1).value);
        assertEquals("lastServer", document.options.get(4).key);
        assertEquals("example.invalid:25565", document.options.get(4).value);
        assertEquals("graphicsMode", document.options.get(5).key);
    }

    @Test
    void importsAllClientOptionsByMergingAndSkipsMalformedEntries() throws Exception {
        Path options = tempDir.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
        Files.writeString(options, String.join("\n",
                "version:3465",
                "lang:en_us",
                "currentOnly:preserved",
                "key_key.forward:key.keyboard.w"
        ), StandardCharsets.UTF_8);

        OptionsFragmentsDocument document = new OptionsFragmentsDocument();
        document.options.add(option("version", "3465"));
        document.options.add(option("lang", "ja_jp"));
        document.options.add(option("graphicsMode", "1"));
        document.options.add(option("lastServer", "example.invalid:25565"));
        document.options.add(option("key_key.forward", "key.keyboard.x"));
        document.options.add(option("malformed", "value\ninjected:true"));

        Path archive = tempDir.resolve("profile.ucp");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(UniversalConfigFormat.PROFILE_OPTIONS_ENTRY,
                JsonDocuments.toJson(document).getBytes(StandardCharsets.UTF_8));
        ZipArchiveWriter.write(archive, entries);

        ProfileDiff diff = new ProfileDiff();
        try (ZipArchiveReader reader = new ZipArchiveReader(archive)) {
            new GenericAdapter().importProfile(tempDir, reader, diff);
        }

        List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        assertTrue(lines.contains("lang:ja_jp"));
        assertTrue(lines.contains("graphicsMode:1"));
        assertTrue(lines.contains("lastServer:example.invalid:25565"));
        assertTrue(lines.contains("currentOnly:preserved"));
        assertTrue(lines.contains("key_key.forward:key.keyboard.w"));
        assertFalse(lines.contains("key_key.forward:key.keyboard.x"));
        assertFalse(lines.contains("injected:true"));
        assertEquals(2, diff.skippedItems.size());
    }

    @Test
    void usesModernKeyValueWhenFirstStartOptionsHaveNoKeyLines() throws Exception {
        Path options = tempDir.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
        Files.writeString(options, String.join("\n",
                "version:2975",
                "lang:en_us",
                "guiScale:0"
        ), StandardCharsets.UTF_8);

        KeybindsDocument document = new KeybindsDocument();
        KeybindsDocument.KeyBindingEntry binding = new KeybindsDocument.KeyBindingEntry();
        binding.id = "key_key.hotbar.2";
        binding.modernValue = "key.keyboard.2";
        binding.legacyValue = 3;
        document.bindings.add(binding);

        Path archive = tempDir.resolve("modern-first-start.ucp");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(UniversalConfigFormat.PROFILE_KEYBINDS_ENTRY,
                JsonDocuments.toJson(document).getBytes(StandardCharsets.UTF_8));
        ZipArchiveWriter.write(archive, entries);

        try (ZipArchiveReader reader = new ZipArchiveReader(archive)) {
            new GenericAdapter().importProfile(tempDir, reader, new ProfileDiff());
        }

        List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        assertTrue(lines.contains("key_key.hotbar.2:key.keyboard.2"));
        assertFalse(lines.contains("key_key.hotbar.2:3"));
    }

    private OptionsFragmentsDocument.OptionEntry option(String key, String value) {
        OptionsFragmentsDocument.OptionEntry option = new OptionsFragmentsDocument.OptionEntry();
        option.key = key;
        option.value = value;
        return option;
    }
}
