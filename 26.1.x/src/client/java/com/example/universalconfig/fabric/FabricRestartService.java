package com.example.universalconfig.fabric;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.UniversalConfigException;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.management.ManagementFactory;
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
            // Preserve JVM argument boundaries and let Fabric resolve the application argument vector. This remains
            // useful in development, where ProcessHandle metadata may be absent or incomplete.
            List<String> javaArguments = CurrentProcessRestartService.buildJavaLaunchArguments(
                    ManagementFactory.getRuntimeMXBean().getInputArguments(),
                    System.getProperty("java.class.path", ""),
                    "net.fabricmc.loader.impl.launch.knot.KnotClient",
                    List.of(loader.getLaunchArguments(false)));
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    loader.getGameDir(), javaArguments);
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Fabric launch arguments.", ex);
        }
    }
}
