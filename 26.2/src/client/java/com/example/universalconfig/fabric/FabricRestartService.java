package com.example.universalconfig.fabric;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.UniversalConfigException;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/**
 * Supplies Fabric's resolved launch arguments to the launcher-independent restart helper.
 */
public final class FabricRestartService {
    private FabricRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            // Reuse the exact argument vector that launched this JVM. Reconstructing it from the classpath and
            // Fabric's application arguments loses development-launcher properties and classpath grouping, which
            // can restart a bare Minecraft client without Universal Config loaded.
            ProcessHandle.Info processInfo = ProcessHandle.current().info();
            List<String> javaArguments = List.of(processInfo.arguments()
                    .orElseThrow(() -> new IllegalStateException("Current Java arguments are unavailable.")));
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    loader.getGameDir(), javaArguments);
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Fabric launch arguments.", ex);
        }
    }
}
