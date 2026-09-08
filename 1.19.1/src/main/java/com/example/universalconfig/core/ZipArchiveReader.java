package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipArchiveReader implements ProfileArchiveReader {
    private final Path archivePath;
    private final ZipFile zipFile;

    public ZipArchiveReader(Path archivePath) throws IOException, UniversalConfigException {
        this.archivePath = archivePath;
        this.zipFile = new ZipFile(archivePath.toFile());
        try {
            FileOperationLogger.info("OPEN_ZIP", archivePath, "read");
            long totalUncompressedSize = 0;
            for (ZipEntry entry : zipFile.stream().toList()) {
                ZipSecurity.validateRelativeEntryName(entry.getName());
                if (!entry.isDirectory()) {
                    totalUncompressedSize = ZipSecurity.validateEntrySizes(
                            entry.getName(), entry.getSize(), entry.getCompressedSize(), totalUncompressedSize);
                }
            }
        } catch (UniversalConfigException | RuntimeException ex) {
            try {
                zipFile.close();
            } catch (IOException closeException) {
                ex.addSuppressed(closeException);
            }
            throw ex;
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
