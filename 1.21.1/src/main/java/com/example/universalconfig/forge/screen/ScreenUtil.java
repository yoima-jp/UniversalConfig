package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.forge.ForgeEnvironmentDetector;
import com.example.universalconfig.forge.MinecraftOptionsReloader;
import com.example.universalconfig.forge.UniversalConfigMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

final class ScreenUtil {
    private ScreenUtil() {
    }

    static ProfileService service() throws UniversalConfigException {
        Minecraft minecraft = UniversalConfigMod.client();
        UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(minecraft.gameDirectory.toPath());
        return new ProfileService(settings);
    }

    static Path instancePath() {
        return UniversalConfigMod.client().gameDirectory.toPath();
    }

    static com.example.universalconfig.core.MinecraftEnvironment environment() {
        return ForgeEnvironmentDetector.detect(instancePath());
    }

    static Component literal(String value) {
        return Component.literal(value == null ? "" : value);
    }

    static Component errorText(Exception ex) {
        if (ex instanceof UniversalConfigException) {
            UniversalConfigException error = (UniversalConfigException) ex;
            if (error.translationKey() != null) {
                Object[] args = error.translationArgs();
                return args == null || args.length == 0
                        ? Component.translatable(error.translationKey())
                        : Component.translatable(error.translationKey(), args);
            }
        }
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Component.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static void renderWidgets(Screen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Screen.render also draws the 1.21.1 blurred background. Custom screens draw their background first, so
        // invoking it here would blur everything already rendered and leave only later widgets sharp.
        for (Renderable renderable : screen.renderables) {
            renderable.render(context, mouseX, mouseY, delta);
        }
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftOptionsReloader.reloadFromDisk(UniversalConfigPaths.optionsFile(instancePath()));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException("Failed to reload Minecraft options; settings may revert before restart.",
                    "message.universal_config.reload_options_failed", ex);
        }
    }
}
