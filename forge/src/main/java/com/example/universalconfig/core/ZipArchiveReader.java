package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipArchiveReader implements ProfileArchiveReader {
    private final Path archivePath;
    private final ZipFile zipFile;

    public ZipArchiveReader(Path archivePath) throws IOException, UniversalConfigException {
        this.archivePath = archivePath;
        this.zipFile = new ZipFile(archivePath.toFile());
        FileOperationLogger.info("OPEN_ZIP", archivePath, "read");
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            ZipSecurity.validateRelativeEntryName(entry.getName());
        }
    }

    @Override
    public boolean exists(String entryName) throws UniversalConfigException {
        ZipSecurity.validateRelativeEntryName(entryName);
        return zipFile.getEntry(entryName) != null;
    }

    @Override
    public InputStream open(String entryName) throws IOException, UniversalConfigException {
        ZipSecurity.validateRelativeEntryName(entryName);
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new UniversalConfigException("Missing ZIP entry: " + entryName);
        }
        FileOperationLogger.info("READ_ZIP_ENTRY", archivePath, entryName);
        return zipFile.getInputStream(entry);
    }

    @Override
    public List<String> entries() {
        List<String> names = new ArrayList<>();
        zipFile.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> names.add(entry.getName()));
        return names;
    }

    @Override
    public Path archivePath() {
        return archivePath;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
        FileOperationLogger.info("CLOSE_ZIP", archivePath, "read");
    }
}
