package com.example.universalconfig.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ZipArchiveWriter {
    private ZipArchiveWriter() {
    }

    public static void write(Path path, Map<String, byte[]> entries) throws IOException, UniversalConfigException {
        Files.createDirectories(path.getParent());
        FileOperationLogger.info("CREATE_DIRECTORY", path.getParent(), "zip parent");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipSecurity.validateRelativeEntryName(entry.getKey());
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
                FileOperationLogger.info("WRITE_ZIP_ENTRY", path, entry.getKey() + " bytes=" + entry.getValue().length);
            }
        }
        FileOperationLogger.info("WRITE_ZIP", path, "entries=" + entries.size());
    }
}
