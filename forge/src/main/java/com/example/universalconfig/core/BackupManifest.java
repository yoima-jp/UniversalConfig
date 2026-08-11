package com.example.universalconfig.core;

import java.util.ArrayList;
import java.util.List;

public final class BackupManifest {
    public String format = UniversalConfigFormat.BACKUP_FORMAT;
    public int formatVersion = UniversalConfigFormat.FORMAT_VERSION;
    public String createdAt;
    public String instancePath;
    public String minecraftVersion;
    public String loader;
    public List<String> files = new ArrayList<>();
}
