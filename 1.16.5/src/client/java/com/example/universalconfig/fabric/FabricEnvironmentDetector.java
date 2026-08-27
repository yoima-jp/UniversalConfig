package com.example.universalconfig.fabric;

import com.example.universalconfig.core.MinecraftEnvironment;
import com.example.universalconfig.core.ModLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.SharedConstants;

import java.nio.file.Path;

public final class FabricEnvironmentDetector {
    private FabricEnvironmentDetector() {
    }

    public static MinecraftEnvironment detect(Path runDirectory) {
        return new MinecraftEnvironment(
                runDirectory,
                SharedConstants.getGameVersion().getName(),
                ModLoader.FABRIC,
                loaderVersion()
        );
    }

    public static MinecraftEnvironment detectFromLoader(Path runDirectory) {
        String minecraftVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata())
                .map(ModMetadata::getVersion)
                .map(version -> version.getFriendlyString())
                .orElse("unknown");
        return new MinecraftEnvironment(
                runDirectory,
                minecraftVersion,
                ModLoader.FABRIC,
                loaderVersion()
        );
    }

    private static String loaderVersion() {
        String loaderVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return loaderVersion;
    }
}

