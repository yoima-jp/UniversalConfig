package com.example.universalconfig.core;

import java.nio.file.Path;
import java.util.List;

public final class BackupSummary {
    private final Path path;
    private final BackupManifest manifest;
    private final List<String> entries;

    public BackupSummary(Path path, BackupManifest manifest, List<String> entries) {
        this.path = path;
        this.manifest = manifest;
        this.entries = entries;
    }

    public Path path() {
        return path;
    }

    public BackupManifest manifest() {
        return manifest;
    }

    public List<String> entries() {
        return entries;
    }
}
