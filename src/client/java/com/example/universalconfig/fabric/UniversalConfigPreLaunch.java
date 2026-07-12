package com.example.universalconfig.fabric;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.nio.file.Path;

public final class UniversalConfigPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory);
            ProfileService service = new ProfileService(settings);
            if (service.readPendingImport(gameDirectory) == null) {
                FileOperationLogger.info("PRELAUNCH_PENDING_IMPORT", gameDirectory, "no pending import");
                return;
            }
            ProfileService.ApplyResult result = service.applyPendingImport(
                    gameDirectory,
                    FabricEnvironmentDetector.detectFromLoader(gameDirectory)
            );
            FileOperationLogger.info("PRELAUNCH_PENDING_IMPORT", gameDirectory, "complete backup=" + result.backupPath());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("PRELAUNCH_PENDING_IMPORT", gameDirectory, "failed", ex);
        }
    }
}
