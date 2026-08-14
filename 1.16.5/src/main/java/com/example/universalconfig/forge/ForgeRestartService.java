package com.example.universalconfig.forge;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;

import java.util.List;

public final class ForgeRestartService {
    private ForgeRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        try {
            // Forge does not expose FabricLoader-style launch arguments. Reuse the already resolved current Java
            // arguments, as the v1.0 Forge builds did, and let the shared service validate the launch shape.
            List<String> javaArguments = CurrentProcessRestartService.currentProcessArguments();
            // ModLauncherのunion CodeSourceは独立Javaプロセスのclasspathとして使えない。
            // Forgeが検出済みの物理ModFileを渡し、RestartHelperを配布Jarから確実に起動する。
            java.nio.file.Path helperClasspath = ModList.get().getModFileById("universal_config")
                    .getFile().getFilePath();
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    FMLPaths.GAMEDIR.get(), javaArguments, helperClasspath);
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Forge launch arguments.", ex);
        }
    }
}
