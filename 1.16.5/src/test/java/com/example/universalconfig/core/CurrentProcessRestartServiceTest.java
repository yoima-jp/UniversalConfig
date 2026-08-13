package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrentProcessRestartServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesForgeUnionLocationToPhysicalJar() throws Exception {
        Path jar = tempDir.resolve("mods with spaces").resolve("universal-config.jar");
        URI location = URI.create("union:" + jar.toUri().getRawPath() + "%23127!/");
        assertEquals(jar.toAbsolutePath().normalize(),
                CurrentProcessRestartService.helperClasspathEntry(location));
    }
}
