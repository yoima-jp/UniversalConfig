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
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import com.example.universalconfig.core.ProfileIcon;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

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

    static ITextComponent literal(String value) {
        return new StringTextComponent(value == null ? "" : value);
    }

    static ITextComponent errorText(Exception ex) {
        return new TranslationTextComponent("screen.universal_config.load_failed");
    }

    static ItemStack iconStack(String iconId) {
        String normalized = ProfileIcon.normalize(iconId);
        if (ProfileIcon.CRAFTING_TABLE.equals(normalized)) return new ItemStack(Blocks.CRAFTING_TABLE);
        if (ProfileIcon.BOOKSHELF.equals(normalized)) return new ItemStack(Blocks.BOOKSHELF);
        if (ProfileIcon.COBBLESTONE.equals(normalized)) return new ItemStack(Blocks.COBBLESTONE);
        if (ProfileIcon.TNT.equals(normalized)) return new ItemStack(Blocks.TNT);
        if (ProfileIcon.CHEST.equals(normalized)) return new ItemStack(Blocks.CHEST);
        if (ProfileIcon.FURNACE.equals(normalized)) return new ItemStack(Blocks.FURNACE);
        if (ProfileIcon.DIAMOND_BLOCK.equals(normalized)) return new ItemStack(Blocks.DIAMOND_BLOCK);
        return new ItemStack(Blocks.GRASS_BLOCK);
    }

    static void enableScissor(int left, int top, int right, int bottom) {
        Minecraft minecraft = UniversalConfigMod.client();
        double scale = minecraft.getWindow().getGuiScale();
        int x = (int) Math.floor(left * scale);
        int y = (int) Math.floor(minecraft.getWindow().getHeight() - bottom * scale);
        int width = (int) Math.ceil((right - left) * scale);
        int height = (int) Math.ceil((bottom - top) * scale);
        RenderSystem.enableScissor(x, y, Math.max(0, width), Math.max(0, height));
    }

    static void disableScissor() {
        RenderSystem.disableScissor();
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftOptionsReloader.reloadFromDisk(UniversalConfigPaths.optionsFile(instancePath()));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException("Minecraftの設定再読み込みに失敗しました。再起動前に設定が戻る可能性があります。", ex);
        }
    }
}
