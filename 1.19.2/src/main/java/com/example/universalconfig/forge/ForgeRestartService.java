package com.example.universalconfig.forge;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.nio.file.Path;

public final class ForgeRestartService {
    private ForgeRestartService() {
    }

    public static void scheduleRestartAfterCurrentProcessExit() throws UniversalConfigException {
        try {
            IModFileInfo modFileInfo = ModList.get().getModFileById(UniversalConfigMod.MOD_ID);
            if (modFileInfo == null || modFileInfo.getFile() == null) {
                throw new UniversalConfigException("Could not locate the Universal Config Forge mod file.");
            }
            Path modFile = modFileInfo.getFile().getFilePath();
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit(
                    FMLPaths.GAMEDIR.get(), modFile);
        } catch (UniversalConfigException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new UniversalConfigException("Could not determine the current Forge launch arguments.", ex);
        }
    }
}
