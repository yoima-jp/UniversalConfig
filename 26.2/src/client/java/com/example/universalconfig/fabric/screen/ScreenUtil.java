package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.fabric.FabricEnvironmentDetector;
import com.example.universalconfig.fabric.UniversalConfigMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import com.example.universalconfig.core.ProfileIcon;
import net.minecraft.resources.Identifier;

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
        return FabricEnvironmentDetector.detect(instancePath());
    }

    static Component literal(String value) {
        return Component.literal(value == null ? "" : value);
    }

    static Component errorText(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Component.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static void setScreen(Minecraft minecraft, Screen screen) {
        UniversalConfigMod.scheduleScreen(minecraft, screen);
    }

    /**
     * Draw profile icons through the 26.2 GUI texture pipeline. This avoids
     * ItemStack component binding during world-free screens while keeping the
     * icon rendering independent of item-model extraction.
     */
    static void drawIcon(GuiGraphicsExtractor context, String iconId, int x, int y, int size) {
        String normalized = ProfileIcon.normalize(iconId);
        Identifier texture = switch (normalized) {
            case ProfileIcon.CRAFTING_TABLE -> Identifier.withDefaultNamespace("textures/block/crafting_table_side.png");
            case ProfileIcon.BOOKSHELF -> Identifier.withDefaultNamespace("textures/block/bookshelf.png");
            case ProfileIcon.COBBLESTONE -> Identifier.withDefaultNamespace("textures/block/cobblestone.png");
            case ProfileIcon.TNT -> Identifier.withDefaultNamespace("textures/block/tnt_side.png");
            case ProfileIcon.CHEST -> Identifier.withDefaultNamespace("textures/entity/chest/normal.png");
            case ProfileIcon.FURNACE -> Identifier.withDefaultNamespace("textures/block/furnace_side.png");
            case ProfileIcon.DIAMOND_BLOCK -> Identifier.withDefaultNamespace("textures/block/diamond_block.png");
            default -> Identifier.withDefaultNamespace("textures/block/grass_block_side.png");
        };
        int textureSize = ProfileIcon.CHEST.equals(normalized) ? 64 : 16;
        context.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                size, size, textureSize, textureSize, -1);
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            Minecraft minecraft = UniversalConfigMod.client();
            minecraft.options.load();
            KeyMapping.resetMapping();
            minecraft.options.save();
            FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "load/resetMapping/write");
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException("Minecraftの設定再読み込みに失敗しました。再起動前に設定が戻る可能性があります。", ex);
        }
    }

    /**
     * 26.2 uses render-state extraction instead of drawing directly into the
     * current framebuffer.  Fill the complete GUI surface with an opaque color
     * so a previous screen or frame can never show through this screen.
     */
    static void renderOpaqueBackground(net.minecraft.client.gui.GuiGraphicsExtractor context) {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF101010);
    }
}
