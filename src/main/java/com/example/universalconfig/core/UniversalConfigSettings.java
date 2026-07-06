package com.example.universalconfig.core;

import java.nio.file.Path;

public final class UniversalConfigSettings {
    private Path rootDirectory;

    public UniversalConfigSettings(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }
}
