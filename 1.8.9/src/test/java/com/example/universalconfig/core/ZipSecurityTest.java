package com.example.universalconfig.core;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZipSecurityTest {
    @Test
    public void rejectsTraversalAbsoluteAndUnsafeSizes() throws Exception {
        assertRejected(new CheckedAction() {
            @Override
            public void run() throws Exception {
                ZipSecurity.validateRelativeEntryName("../options.txt");
            }
        });
        assertRejected(new CheckedAction() {
            @Override
            public void run() throws Exception {
                ZipSecurity.validateRelativeEntryName("C:\\Windows\\system.ini");
            }
        });
        assertRejected(new CheckedAction() {
            @Override
            public void run() throws Exception {
                ZipSecurity.validateEntrySizes(
                        "large.txt", ZipSecurity.MAX_ENTRY_UNCOMPRESSED_BYTES + 1, 1, 0);
            }
        });
        assertRejected(new CheckedAction() {
            @Override
            public void run() throws Exception {
                ZipSecurity.validateEntrySizes(
                        "ratio.txt", ZipSecurity.MAX_COMPRESSION_RATIO + 1, 1, 0);
            }
        });
        assertRejected(new CheckedAction() {
            @Override
            public void run() throws Exception {
                ZipSecurity.validateEntrySizes(
                        "total.txt", 1, 1, ZipSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES);
            }
        });
    }

    @Test
    public void acceptsSafeEntryWithinLimits() throws Exception {
        Path root = Paths.get("build", "tmp", "zip-root");
        assertEquals(root.toAbsolutePath().normalize().resolve("profile/config.json"),
                ZipSecurity.safeResolve(root, "profile/config.json"));
        assertEquals(300L, ZipSecurity.validateEntrySizes("safe.txt", 300, 3, 0));
    }

    private static void assertRejected(CheckedAction action) throws Exception {
        try {
            action.run();
            fail("Expected unsafe ZIP metadata to be rejected");
        } catch (UniversalConfigException expected) {
            // The specific message is intentionally not asserted because it is an internal diagnostic.
        }
    }

    private interface CheckedAction {
        void run() throws Exception;
    }
}
