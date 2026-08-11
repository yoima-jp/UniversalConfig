package com.example.universalconfig.core;

public final class PendingImport {
    public String format = UniversalConfigFormat.PENDING_IMPORT_FORMAT;
    public int formatVersion = UniversalConfigFormat.FORMAT_VERSION;
    public String profilePath;
    public String scheduledAt;
    public String minecraftVersion;
    public String loader;
    public String loaderVersion;
}
