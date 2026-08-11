package com.example.universalconfig.forge;

import com.example.universalconfig.core.MinecraftEnvironment;
import com.example.universalconfig.core.ModLoader;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.loading.FMLLoader;

import java.nio.file.Path;

public final class ForgeEnvironmentDetector {
    private ForgeEnvironmentDetector() {
    }

    public static MinecraftEnvironment detect(Path gameDirectory) {
        String forgeVersion = FMLLoader.versionInfo().forgeVersion();
        return new MinecraftEnvironment(
                gameDirectory,
                SharedConstants.getCurrentVersion().name(),
                ModLoader.FORGE,
                forgeVersion
        );
    }
}
