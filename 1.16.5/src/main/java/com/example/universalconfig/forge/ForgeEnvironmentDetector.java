package com.example.universalconfig.forge;

import com.example.universalconfig.core.MinecraftEnvironment;
import com.example.universalconfig.core.ModLoader;
import net.minecraft.util.SharedConstants;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

public final class ForgeEnvironmentDetector {
    private ForgeEnvironmentDetector() {
    }

    public static MinecraftEnvironment detect(Path gameDirectory) {
        String forgeVersion = ModList.get().getModContainerById("forge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        return new MinecraftEnvironment(
                gameDirectory,
                SharedConstants.getCurrentVersion().getName(),
                ModLoader.FORGE,
                forgeVersion
        );
    }
}
