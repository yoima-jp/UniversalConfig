package com.example.universalconfig.core;

import java.nio.file.Path;

public final class UniversalConfigSettings {
    private Path rootDirectory;
    private Path defaultProfilePath;

    public UniversalConfigSettings(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path rootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path defaultProfilePath() {
        return defaultProfilePath;
    }

    public void setDefaultProfilePath(Path defaultProfilePath) {
        this.defaultProfilePath = defaultProfilePath;
    }
}
