package com.example.universalconfig.fabric;

import com.example.universalconfig.core.GeneratedFileCleaner;
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
        String operation = "PRELAUNCH_INITIALIZE";
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory);
            ProfileService service = new ProfileService(settings);
            runGeneratedFileCleanup(settings);
            if (service.readPendingImport(gameDirectory) != null) {
                operation = "PRELAUNCH_PENDING_IMPORT";
                ProfileService.ApplyResult result = service.applyPendingImport(
                        gameDirectory,
                        FabricEnvironmentDetector.detectFromLoader(gameDirectory)
                );
                FileOperationLogger.info("PRELAUNCH_PENDING_IMPORT", gameDirectory,
                        "complete backup=" + result.backupPath());
                return;
            }

            operation = "PRELAUNCH_DEFAULT_PROFILE";
            ProfileService.ApplyResult result = service.applyDefaultProfileOnFirstStart(
                    gameDirectory,
                    FabricEnvironmentDetector.detectFromLoader(gameDirectory)
            );
            if (result == null) {
                FileOperationLogger.info("PRELAUNCH_DEFAULT_PROFILE", gameDirectory, "not applied");
            } else {
                FileOperationLogger.info("PRELAUNCH_DEFAULT_PROFILE", gameDirectory,
                        "complete backup=" + result.backupPath());
            }
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure(operation, gameDirectory, "failed", ex);
        }
    }

    private void runGeneratedFileCleanup(UniversalConfigSettings settings) {
        try {
            // Cleanup is intentionally isolated from pending/default-profile application so a stale file can never
            // prevent startup work from continuing.
            new GeneratedFileCleaner(settings).cleanup();
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("CLEANUP", settings.rootDirectory(), "startup cleanup failed", ex);
        }
    }
}

