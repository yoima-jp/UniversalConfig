package com.example.universalconfig.core;

import java.nio.file.Path;

public final class MinecraftEnvironment {
    private final Path instancePath;
    private final String minecraftVersion;
    private final ModLoader loader;
    private final String loaderVersion;

    public MinecraftEnvironment(Path instancePath, String minecraftVersion, ModLoader loader, String loaderVersion) {
        this.instancePath = instancePath;
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
    }

    public Path instancePath() {
        return instancePath;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public ModLoader loader() {
        return loader;
    }

    public String loaderVersion() {
        return loaderVersion;
    }

    public String loaderId() {
        return loader == null ? ModLoader.UNKNOWN.id() : loader.id();
    }
}
