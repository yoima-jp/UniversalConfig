package com.example.universalconfig.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ZipSecurity {
    /** Maximum uncompressed size accepted for one profile or backup entry. */
    public static final long MAX_ENTRY_UNCOMPRESSED_BYTES = 32L * 1024L * 1024L;

    /** Maximum combined uncompressed size accepted for one archive. */
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L;

    /** Maximum declared expansion ratio accepted for a non-empty entry. */
    public static final long MAX_COMPRESSION_RATIO = 1_000L;

    private ZipSecurity() {
    }

    public static void validateRelativeEntryName(String entryName) throws UniversalConfigException {
        if (entryName == null || entryName.trim().isEmpty()) {
            throw new UniversalConfigException("ZIP entry name is empty.");
        }
        String normalizedSlashes = entryName.replace('\\', '/');
        if (normalizedSlashes.startsWith("/")
                || hasWindowsDrivePrefix(normalizedSlashes)
                || normalizedSlashes.contains("../")
                || normalizedSlashes.equals("..")) {
            throw new UniversalConfigException("Unsafe ZIP entry path rejected: " + entryName);
        }
        Path path = Paths.get(normalizedSlashes);
        if (path.isAbsolute()) {
            throw new UniversalConfigException("Absolute ZIP entry path rejected: " + entryName);
        }
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                throw new UniversalConfigException("Parent traversal ZIP entry rejected: " + entryName);
            }
        }
    }

    private static boolean hasWindowsDrivePrefix(String entryName) {
        return entryName.length() >= 2
                && Character.isLetter(entryName.charAt(0))
                && entryName.charAt(1) == ':';
    }

    public static Path safeResolve(Path destinationRoot, String entryName) throws UniversalConfigException {
        validateRelativeEntryName(entryName);
        Path root = destinationRoot.toAbsolutePath().normalize();
        Path resolved = root.resolve(entryName.replace('\\', '/')).normalize();
        if (!resolved.startsWith(root)) {
            throw new UniversalConfigException("ZIP entry escapes destination: " + entryName);
        }
        return resolved;
    }

    /**
     * Validates central-directory size metadata before any archive payload is read.
     * The returned value is the accumulated uncompressed size for the archive.
     */
    public static long validateEntrySizes(
            String entryName,
            long uncompressedSize,
            long compressedSize,
            long currentTotal
    ) throws UniversalConfigException {
        if (uncompressedSize < 0 || compressedSize < 0) {
            throw new UniversalConfigException("ZIP entry size metadata is missing: " + entryName);
        }
        long total = validateUncompressedSize(entryName, uncompressedSize, currentTotal);
        if (uncompressedSize > 0
                && (compressedSize == 0 || uncompressedSize / compressedSize > MAX_COMPRESSION_RATIO)) {
            throw new UniversalConfigException("ZIP entry compression ratio is unsafe: " + entryName);
        }
        return total;
    }

    public static long validateUncompressedSize(
            String entryName,
            long uncompressedSize,
            long currentTotal
    ) throws UniversalConfigException {
        if (uncompressedSize < 0) {
            throw new UniversalConfigException("ZIP entry size is invalid: " + entryName);
        }
        if (uncompressedSize > MAX_ENTRY_UNCOMPRESSED_BYTES) {
            throw new UniversalConfigException("ZIP entry exceeds the uncompressed size limit: " + entryName);
        }
        if (currentTotal < 0 || currentTotal > MAX_TOTAL_UNCOMPRESSED_BYTES - uncompressedSize) {
            throw new UniversalConfigException("ZIP archive exceeds the total uncompressed size limit.");
        }
        return currentTotal + uncompressedSize;
    }
}
