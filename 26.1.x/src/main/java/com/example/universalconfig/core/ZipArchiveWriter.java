package com.example.universalconfig.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * Rebuilds an archive while streaming unchanged entries and replacing only the manifest.
     * This keeps duplicate-name imports from loading an untrusted archive's entire contents into memory.
     */
    public static void writeWithReplacedManifest(
            Path path,
            ProfileArchiveReader source,
            byte[] manifestBytes
    ) throws IOException, UniversalConfigException {
        Files.createDirectories(path.getParent());
        FileOperationLogger.info("CREATE_DIRECTORY", path.getParent(), "zip parent");
        ChecksumDocument checksums = new ChecksumDocument();
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entryName : source.entries()) {
                if (UniversalConfigFormat.CHECKSUMS_ENTRY.equals(entryName)) {
                    continue;
                }
                ZipSecurity.validateRelativeEntryName(entryName);
                output.putNextEntry(new ZipEntry(entryName));
                String checksum;
                if (UniversalConfigFormat.MANIFEST_ENTRY.equals(entryName)) {
                    output.write(manifestBytes);
                    checksum = Checksums.sha256(manifestBytes);
                } else {
                    try (InputStream input = source.open(entryName)) {
                        checksum = copyAndHash(input, output);
                    }
                }
                output.closeEntry();
                checksums.files.put(entryName, checksum);
                FileOperationLogger.info("WRITE_ZIP_ENTRY", path, entryName);
            }

            byte[] checksumBytes = JsonDocuments.toJson(checksums).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.putNextEntry(new ZipEntry(UniversalConfigFormat.CHECKSUMS_ENTRY));
            output.write(checksumBytes);
            output.closeEntry();
            FileOperationLogger.info("WRITE_ZIP_ENTRY", path, UniversalConfigFormat.CHECKSUMS_ENTRY);
        }
        FileOperationLogger.info("WRITE_ZIP", path, "entries=" + (checksums.files.size() + 1));
    }

    private static String copyAndHash(InputStream input, ZipOutputStream output)
            throws IOException, UniversalConfigException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            return Checksums.toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new UniversalConfigException("SHA-256 is not available.", ex);
        }
    }
}
