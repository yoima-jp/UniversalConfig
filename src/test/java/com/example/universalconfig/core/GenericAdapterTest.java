package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void extractsSafeClientOptionsFragments() throws Exception {
        Path options = tempDir.resolve(UniversalConfigFormat.OPTIONS_FILE_NAME);
        Files.writeString(options, String.join("\n",
                "lang:ja_jp",
                "soundCategory_master:0.25",
                "soundCategory_music:0.0",
                "guiScale:3",
                "key_key.forward:key.keyboard.w",
                "lastServer:example.invalid"
        ), StandardCharsets.UTF_8);

        OptionsFragmentsDocument document = new GenericAdapter().extractOptionsFragments(options);

        assertEquals(4, document.options.size());
        assertEquals("lang", document.options.get(0).key);
        assertEquals("ja_jp", document.options.get(0).value);
        assertEquals("soundCategory_master", document.options.get(1).key);
        assertEquals("0.25", document.options.get(1).value);
    }
}
