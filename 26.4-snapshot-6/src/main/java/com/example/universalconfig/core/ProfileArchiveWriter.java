package com.example.universalconfig.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public interface ProfileArchiveWriter extends AutoCloseable {
    void addBytes(String entryName, byte[] bytes) throws IOException, UniversalConfigException;

    default void addString(String entryName, String content) throws IOException, UniversalConfigException {
        addBytes(entryName, content.getBytes(StandardCharsets.UTF_8));
    }

    Map<String, byte[]> pendingEntries();

    final class InMemory implements ProfileArchiveWriter {
        private final Map<String, byte[]> entries = new LinkedHashMap<>();

        @Override
        public void addBytes(String entryName, byte[] bytes) throws UniversalConfigException {
            ZipSecurity.validateRelativeEntryName(entryName);
            entries.put(entryName, bytes);
            FileOperationLogger.info("QUEUE_ZIP_ENTRY", null, entryName + " bytes=" + bytes.length);
        }

        @Override
        public Map<String, byte[]> pendingEntries() {
            return entries;
        }

        @Override
        public void close() {
        }
    }
}
