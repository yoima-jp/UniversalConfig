package com.example.universalconfig.core;

import java.nio.file.Path;

public final class ProfileSummary {
    private final Path path;
    private final ProfileManifest manifest;

    public ProfileSummary(Path path, ProfileManifest manifest) {
        this.path = path;
        this.manifest = manifest;
    }

    public Path path() {
        return path;
    }

    public ProfileManifest manifest() {
        return manifest;
    }
}
