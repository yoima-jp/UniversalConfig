package com.example.universalconfig.fabric;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.fabric.screen.ProfileListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class UniversalConfigMod implements ClientModInitializer {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;

    private KeyBinding openKey;
    private boolean pendingImportLogged;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.universal_config.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.universal_config"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null || client.currentScreen instanceof TitleScreen) {
                    client.setScreen(new ProfileListScreen(client.currentScreen));
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                logPendingImportStateOnce(client);
                Screens.getButtons(screen).add(ButtonWidget.builder(
                        Text.literal("Universal Config"),
                        button -> client.setScreen(new ProfileListScreen(screen))
                ).dimensions(8, 8, 120, 20).build());
            }
        });
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private void logPendingImportStateOnce(MinecraftClient client) {
        if (pendingImportLogged) {
            return;
        }
        pendingImportLogged = true;
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(client.runDirectory.toPath());
            FileOperationLogger.info("TITLE_SCREEN_READY", client.runDirectory.toPath(),
                    "settingsRoot=" + settings.rootDirectory());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("TITLE_SCREEN_READY", client.runDirectory.toPath(), "failed", ex);
        }
    }
}
