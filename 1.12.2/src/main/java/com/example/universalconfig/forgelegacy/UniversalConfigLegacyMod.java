package com.example.universalconfig.forgelegacy;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.GeneratedFileCleaner;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.nio.file.Path;

@Mod(modid = UniversalConfigLegacyMod.MOD_ID, name = "Universal Config", version = "1.1.0",
        acceptedMinecraftVersions = "[1.12.2]", clientSideOnly = true,
        guiFactory = "com.example.universalconfig.forgelegacy.UniversalConfigGuiFactory")
public final class UniversalConfigLegacyMod {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path gameDirectory = event.getModConfigurationDirectory().toPath().toAbsolutePath().normalize().getParent();
        String operation = "STARTUP_INITIALIZE";
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory);
            ProfileService service = new ProfileService(settings);
            try {
                new GeneratedFileCleaner(settings).cleanup();
            } catch (RuntimeException ex) {
                FileOperationLogger.failure("CLEANUP", settings.rootDirectory(), "startup cleanup failed", ex);
            }
            ProfileService.ApplyResult result;
            if (service.readPendingImport(gameDirectory) != null) {
                operation = "STARTUP_PENDING_IMPORT";
                result = service.applyPendingImport(gameDirectory, LegacyPlatform.environment(gameDirectory));
            } else {
                operation = "STARTUP_DEFAULT_PROFILE";
                result = service.applyDefaultProfileOnFirstStart(gameDirectory, LegacyPlatform.environment(gameDirectory));
            }
            FileOperationLogger.info(operation, gameDirectory, result == null ? "not applied" : "complete");
        } catch (UniversalConfigException ex) {
            FileOperationLogger.failure(operation, gameDirectory, "failed", ex);
        } catch (RuntimeException ex) {
            FileOperationLogger.failure(operation, gameDirectory, "unexpected failure", ex);
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 設定画面はタイトル画面のボタンから開けるため、キーバインドは登録しない（Issue #35）。
        // ゲーム中に頻繁に開く用途ではなく、未割り当てのキーがキー設定一覧に残ると混乱を招く。
        // 過去に登録していたキーが options.txt に残っていても、未登録の KeyBinding は無視される。
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new LegacyTitleButtonHandler());
        try {
            // Always normalize the loaded locale. A profile from a newer Minecraft version can leave a lowercase
            // locale ID behind even after its one-time default application marker has already been consumed.
            LegacyPlatform.reloadOptions();
        } catch (UniversalConfigException ex) {
            FileOperationLogger.failure("STARTUP_RELOAD_OPTIONS", LegacyPlatform.gameDirectory(), "failed", ex);
        }
    }
}
