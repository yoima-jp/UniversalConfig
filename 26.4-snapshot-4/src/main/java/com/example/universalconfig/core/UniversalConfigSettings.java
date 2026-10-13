package com.example.universalconfig.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UniversalConfigSettings {
    private Path rootDirectory;
    private Path defaultProfilePath;
    private List<String> profileOrder = new ArrayList<>();

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

    public List<String> profileOrder() {
        return List.copyOf(profileOrder);
    }

    public void setProfileOrder(List<String> profileOrder) {
        this.profileOrder = new ArrayList<>();
        if (profileOrder == null) {
            return;
        }
        for (String profileKey : profileOrder) {
            if (profileKey != null && !profileKey.isBlank() && !this.profileOrder.contains(profileKey)) {
                this.profileOrder.add(profileKey);
            }
        }
    }
}
