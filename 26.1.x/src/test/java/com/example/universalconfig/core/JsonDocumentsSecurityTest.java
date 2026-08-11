package com.example.universalconfig.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonDocumentsSecurityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsOversizedJsonFromUntrustedArchiveBeforeParsing() {
        final byte[] oversized = new byte[1_048_577];
        ProfileArchiveReader reader = new ProfileArchiveReader() {
            @Override
            public InputStream open(String entryName) {
                return new ByteArrayInputStream(oversized);
            }

            @Override
            public boolean exists(String entryName) {
                return true;
            }

            @Override
            public List<String> entries() {
                return Collections.singletonList("profile.json");
            }

            @Override
            public Path archivePath() {
                return temporaryDirectory.resolve("untrusted.ucp");
            }

            @Override
            public void close() {
            }
        };

        assertThrows(UniversalConfigException.class,
                () -> JsonDocuments.read(reader, "profile.json", ProfileManifest.class));
    }
}
