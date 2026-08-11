package com.example.universalconfig.forgelegacy;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.GeneratedFileCleaner;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;

@Mod(modid = UniversalConfigLegacyMod.MOD_ID, name = "Universal Config", version = "1.0.0",
        acceptedMinecraftVersions = "[1.12.2]", clientSideOnly = true,
        guiFactory = "com.example.universalconfig.forgelegacy.UniversalConfigGuiFactory")
public final class UniversalConfigLegacyMod {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static KeyBinding openKey;

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
        openKey = new KeyBinding("key.universal_config.open", Keyboard.KEY_U, "category.universal_config");
        ClientRegistry.registerKeyBinding(openKey);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new LegacyTitleButtonHandler());
        try {
            // Always normalize the loaded locale. A profile from a newer Minecraft version can leave a lowercase
            // locale ID behind even after its one-time default application marker has already been consumed.
            LegacyPlatform.reloadOptions();
        } catch (UniversalConfigException ex) {
            FileOperationLogger.failure("STARTUP_RELOAD_OPTIONS", LegacyPlatform.gameDirectory(), "failed", ex);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && openKey != null && openKey.isPressed()) {
            Minecraft minecraft = Minecraft.getMinecraft();
            minecraft.displayGuiScreen(new LegacyScreens.ProfileList(minecraft.currentScreen));
        }
    }
}
