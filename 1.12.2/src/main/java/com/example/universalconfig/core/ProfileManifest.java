package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class ProfileManifest {
    public String format = UniversalConfigFormat.PROFILE_FORMAT;
    public int formatVersion = UniversalConfigFormat.FORMAT_VERSION;
    public String id;
    public String name;
    public String description;
    public String icon;
    public String createdAt;
    public String updatedAt;
    public Source source = new Source();
    public Compatibility compatibility = new Compatibility();
    public Includes includes = new Includes();

    public static final class Source {
        public String minecraftVersion;
        public String loader;
        public String loaderVersion;
    }

    public static final class Compatibility {
        public String minecraftVersionRange = "1.7.10-latest";
        public List<String> testedVersions = new ArrayList<>();
        public String mode = "best-effort";
    }

    public static final class Includes {
        public boolean keybinds = true;
        public boolean modConfigs = true;
        public boolean clientOptions = true;
    }
}
