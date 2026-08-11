package com.example.universalconfig.forge;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgeRestartService {
    private ForgeRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        try {
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    FMLPaths.GAMEDIR.get());
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Forge launch arguments.", ex);
        }
    }
}
