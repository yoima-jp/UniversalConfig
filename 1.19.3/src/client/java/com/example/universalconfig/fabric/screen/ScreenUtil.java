package com.example.universalconfig.fabric.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.fabric.FabricEnvironmentDetector;
import com.example.universalconfig.fabric.UniversalConfigMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.function.Function;

final class ScreenUtil {
    private ScreenUtil() {
    }

    static ProfileService service() throws UniversalConfigException {
        MinecraftClient client = UniversalConfigMod.client();
        UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(client.runDirectory.toPath());
        return new ProfileService(settings);
    }

    static Path instancePath() {
        return UniversalConfigMod.client().runDirectory.toPath();
    }

    static com.example.universalconfig.core.MinecraftEnvironment environment() {
        return FabricEnvironmentDetector.detect(instancePath());
    }

    static Text literal(String value) {
        return Text.literal(value == null ? "" : value);
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, Text text,
                                   int x, int y, int color) {
        DrawableHelper.drawTextWithShadow(matrices, renderer, text, x, y, color);
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, OrderedText text,
                                   int x, int y, int color) {
        DrawableHelper.drawWithShadow(matrices, renderer, text, x, y, color);
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, String text,
                                   int x, int y, int color) {
        DrawableHelper.drawStringWithShadow(matrices, renderer, text == null ? "" : text, x, y, color);
    }

    static void drawCenteredTextWithShadow(MatrixStack matrices, TextRenderer renderer, Text text,
                                           int centerX, int y, int color) {
        DrawableHelper.drawWithShadow(matrices, renderer, text.asOrderedText(),
                centerX - renderer.getWidth(text) / 2, y, color);
    }

    static void fill(MatrixStack matrices, int left, int top, int right, int bottom, int color) {
        DrawableHelper.fill(matrices, left, top, right, bottom, color);
    }

    static void enableScissor(int left, int top, int right, int bottom) {
        DrawableHelper.enableScissor(left, top, right, bottom);
    }

    static void disableScissor() {
        DrawableHelper.disableScissor();
    }

    static void drawItem(MatrixStack matrices, ItemStack stack, int x, int y) {
        drawItem(matrices, stack, x, y, 1.0F);
    }

    static void drawItem(MatrixStack matrices, ItemStack stack, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getItemRenderer().getModel(stack, client.world, client.player, 0);
        AbstractTexture atlas = client.getTextureManager().getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        atlas.setFilter(false, false);
        RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.translate(x, y, 100.0F);
        modelView.translate(8.0F * scale, 8.0F * scale, 0.0F);
        modelView.scale(scale, -scale, 1.0F);
        modelView.scale(16.0F, 16.0F, 16.0F);
        RenderSystem.applyModelViewMatrix();
        boolean disableLighting = !model.isSideLit();
        if (disableLighting) {
            DiffuseLighting.disableGuiDepthLighting();
        }
        try {
            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
            client.getItemRenderer().renderItem(stack, ModelTransformation.Mode.GUI, false, new MatrixStack(), consumers,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, model);
            consumers.draw();
        } finally {
            RenderSystem.enableDepthTest();
            if (disableLighting) {
                DiffuseLighting.enableGuiDepthLighting();
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /**
     * Keeps the shared screen code on the builder API available in 1.19.3.
     */
    static LegacyButtonBuilder buttonBuilder(Text message, ButtonWidget.PressAction onPress) {
        return new LegacyButtonBuilder(message, onPress);
    }

    static final class LegacyButtonBuilder {
        private final Text message;
        private final ButtonWidget.PressAction onPress;
        private int x;
        private int y;
        private int width;
        private int height;

        private LegacyButtonBuilder(Text message, ButtonWidget.PressAction onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        LegacyButtonBuilder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        LegacyButtonBuilder narrationSupplier(Function<ButtonWidget, Text> ignored) {
            return this;
        }

        ButtonWidget build() {
            return ButtonWidget.builder(message, onPress)
                    .dimensions(x, y, width, height)
                    .build();
        }
    }

    static Text errorText(Exception ex) {
        if (ex instanceof UniversalConfigException ucex && ucex.translationKey() != null) {
            Object[] args = ucex.translationArgs();
            return args == null || args.length == 0
                    ? Text.translatable(ucex.translationKey())
                    : Text.translatable(ucex.translationKey(), args);
        }

        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Text.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftClient client = UniversalConfigMod.client();
            client.options.load();
            KeyBinding.updateKeysByCode();
            client.options.write();
            FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "load/updateKeysByCode/write");
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException(
                    "Failed to reload Minecraft options; settings may revert before restart.",
                    "message.universal_config.reload_options_failed",
                    ex);
        }
    }
}
