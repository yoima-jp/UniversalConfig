package com.example.universalconfig.core;

import java.nio.file.Path;

public final class ZipSecurity {
    private ZipSecurity() {
    }

    public static void validateRelativeEntryName(String entryName) throws UniversalConfigException {
        if (entryName == null || entryName.isBlank()) {
            throw new UniversalConfigException("ZIP entry name is empty.");
        }
        String normalizedSlashes = entryName.replace('\\', '/');
        if (normalizedSlashes.startsWith("/") || normalizedSlashes.contains("../") || normalizedSlashes.equals("..")) {
            throw new UniversalConfigException("Unsafe ZIP entry path rejected: " + entryName);
        }
        Path path = Path.of(normalizedSlashes);
        if (path.isAbsolute()) {
            throw new UniversalConfigException("Absolute ZIP entry path rejected: " + entryName);
        }
        for (Path part : path) {
            if ("..".equals(part.toString())) {
                throw new UniversalConfigException("Parent traversal ZIP entry rejected: " + entryName);
            }
        }
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
