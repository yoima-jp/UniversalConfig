package com.example.universalconfig.fabric;

import com.example.universalconfig.core.MinecraftEnvironment;
import com.example.universalconfig.core.ModLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;

import java.nio.file.Path;

public final class FabricEnvironmentDetector {
    private FabricEnvironmentDetector() {
    }

    public static MinecraftEnvironment detect(Path runDirectory) {
        String loaderVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return new MinecraftEnvironment(
                runDirectory,
                SharedConstants.getGameVersion().getName(),
                ModLoader.FABRIC,
                loaderVersion
        );
    }
}
