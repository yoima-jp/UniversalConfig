package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class BackupManifest {
    public String format = "universal-config-backup";
    public int formatVersion = 1;
    public String createdAt;
    public String instancePath;
    public String minecraftVersion;
    public String loader;
    public List<String> files = new ArrayList<>();
}
