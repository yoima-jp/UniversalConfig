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
    private static final String KNOT_CLIENT_MAIN_CLASS = "net.fabricmc.loader.impl.launch.knot.KnotClient";

    private FabricRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            // Fabric retains the original application arguments as an array, while RuntimeMXBean retains JVM
            // argument boundaries. Combining these sources avoids parsing a quoted command line and preserves paths
            // containing spaces and account tokens for both supported and unsupported launchers.
            List<String> javaArguments = CurrentProcessRestartService.buildJavaLaunchArguments(
                    ManagementFactory.getRuntimeMXBean().getInputArguments(),
                    System.getProperty("java.class.path", ""),
                    KNOT_CLIENT_MAIN_CLASS,
                    com.example.universalconfig.core.Java8Compat.listOf(loader.getLaunchArguments(false))
            );
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    loader.getGameDir(), javaArguments);
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Fabric launch arguments.", ex);
        }
    }
}
