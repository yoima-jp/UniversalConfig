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
import net.minecraft.client.render.GameRenderer;
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
import net.minecraft.util.math.MathHelper;

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
     * Provides the 1.20-style builder call shape on 1.19.1, whose ButtonWidget
     * API still uses the direct constructor.
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
            return new VanillaButtonWidget(x, y, width, height, message, onPress);
        }
    }

    /**
     * Keeps the 1.19.1 ButtonWidget contract while avoiding a full 20px widget texture stretch when a screen uses
     * a 24px button. Newer Minecraft versions render the same vanilla texture as a nine-slice, so the top and bottom
     * edges stay crisp and the middle section absorbs the extra height.
     */
    private static final class VanillaButtonWidget extends ButtonWidget {
        private VanillaButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
            MinecraftClient client = MinecraftClient.getInstance();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, WIDGETS_TEXTURE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();

            int textureY = 46 + getYImage(isHovered()) * 20;
            // Match 1.20.1's DrawContext.drawNineSlicedTexture(WIDGETS_TEXTURE, ..., 20, 4, 200, 20, 0, textureY).
            // The 1.19.1 helper has no nine-slice equivalent, so keep the 20px horizontal and 4px vertical
            // borders fixed and repeat only the center regions. Splitting at width / 2 stretches the texture's
            // interior and can expose the adjacent row when a 24px action button is rendered.
            int leftWidth = Math.min(20, width / 2);
            int rightWidth = leftWidth;
            int topHeight = Math.min(4, height / 2);
            int bottomHeight = topHeight;
            int middleWidth = Math.max(0, width - leftWidth - rightWidth);
            int middleHeight = Math.max(0, height - topHeight - bottomHeight);
            int rightU = 200 - rightWidth;
            int middleU = leftWidth;
            int middleTextureWidth = Math.max(0, 200 - leftWidth - rightWidth);
            int middleTextureHeight = Math.max(0, 20 - topHeight - bottomHeight);

            drawTexture(context, x, y, 0, textureY, leftWidth, topHeight);
            drawTextureRepeated(context, x + leftWidth, y, middleWidth, topHeight,
                    middleU, textureY, middleTextureWidth, topHeight);
            drawTexture(context, x + width - rightWidth, y, rightU, textureY, rightWidth, topHeight);

            drawTextureRepeated(context, x, y + topHeight, leftWidth, middleHeight,
                    0, textureY + topHeight, leftWidth, middleTextureHeight);
            drawTextureRepeated(context, x + leftWidth, y + topHeight, middleWidth, middleHeight,
                    middleU, textureY + topHeight, middleTextureWidth, middleTextureHeight);
            drawTextureRepeated(context, x + width - rightWidth, y + topHeight, rightWidth, middleHeight,
                    rightU, textureY + topHeight, rightWidth, middleTextureHeight);

            drawTexture(context, x, y + height - bottomHeight, 0, textureY + 20 - bottomHeight,
                    leftWidth, bottomHeight);
            drawTextureRepeated(context, x + leftWidth, y + height - bottomHeight, middleWidth, bottomHeight,
                    middleU, textureY + 20 - bottomHeight, middleTextureWidth, bottomHeight);
            drawTexture(context, x + width - rightWidth, y + height - bottomHeight,
                    rightU, textureY + 20 - bottomHeight, rightWidth, bottomHeight);

            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            renderBackground(context, client, mouseX, mouseY);
            int textColor = active ? 0xFFFFFF : 0xA0A0A0;
            DrawableHelper.drawCenteredText(context, client.textRenderer, getMessage(), x + width / 2,
                    y + (height - 8) / 2, textColor | (MathHelper.ceil(alpha * 255.0F) << 24));
        }

        private void drawTextureRepeated(MatrixStack context, int x, int y, int width, int height,
                                          int u, int v, int textureWidth, int textureHeight) {
            if (width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) {
                return;
            }
            for (int offsetY = 0; offsetY < height; offsetY += textureHeight) {
                int tileHeight = Math.min(textureHeight, height - offsetY);
                for (int offsetX = 0; offsetX < width; offsetX += textureWidth) {
                    int tileWidth = Math.min(textureWidth, width - offsetX);
                    drawTexture(context, x + offsetX, y + offsetY, u, v, tileWidth, tileHeight);
                }
            }
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
