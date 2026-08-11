package com.example.universalconfig.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ZipSecurity {
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
}
