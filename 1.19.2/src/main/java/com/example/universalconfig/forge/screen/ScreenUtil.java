package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.forge.ForgeEnvironmentDetector;
import com.example.universalconfig.forge.MinecraftOptionsReloader;
import com.example.universalconfig.forge.UniversalConfigMod;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

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
        return Component.translatable("screen.universal_config.load_failed");
    }

    static void drawProfileIcon(int x, int y, int size, String iconId) {
        Minecraft minecraft = UniversalConfigMod.client();
        float scale = size / 16.0F;
        PoseStack modelView = RenderSystem.getModelViewStack();
        // ItemRenderer still consumes RenderSystem's model-view stack in 1.19. Applying this transform only to the
        // Screen PoseStack leaves scaled icons at the origin where the list/detail scissors hide them.
        modelView.pushPose();
        try {
            modelView.translate(x, y, 0.0D);
            modelView.scale(scale, scale, 1.0F);
            RenderSystem.applyModelViewMatrix();
            minecraft.getItemRenderer().renderAndDecorateItem(iconStack(iconId), 0, 0);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    static ItemStack iconStack(String iconId) {
        return switch (ProfileIcon.normalize(iconId)) {
            case ProfileIcon.CRAFTING_TABLE -> new ItemStack(Blocks.CRAFTING_TABLE);
            case ProfileIcon.BOOKSHELF -> new ItemStack(Blocks.BOOKSHELF);
            case ProfileIcon.COBBLESTONE -> new ItemStack(Blocks.COBBLESTONE);
            case ProfileIcon.TNT -> new ItemStack(Blocks.TNT);
            case ProfileIcon.CHEST -> new ItemStack(Blocks.CHEST);
            case ProfileIcon.FURNACE -> new ItemStack(Blocks.FURNACE);
            case ProfileIcon.DIAMOND_BLOCK -> new ItemStack(Blocks.DIAMOND_BLOCK);
            default -> new ItemStack(Blocks.GRASS_BLOCK);
        };
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

    static void renderWidgets(Screen screen, PoseStack context, int mouseX, int mouseY, float delta) {
        for (net.minecraft.client.gui.components.Widget renderable : screen.renderables) {
            renderable.render(context, mouseX, mouseY, delta);
        }
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
