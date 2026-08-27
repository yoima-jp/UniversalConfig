package com.example.universalconfig.core;

import java.nio.file.Path;

public record MinecraftEnvironment(
        Path instancePath,
        String minecraftVersion,
        ModLoader loader,
        String loaderVersion
) {
    public String loaderId() {
        return loader == null ? ModLoader.UNKNOWN.id() : loader.id();
    }
}

