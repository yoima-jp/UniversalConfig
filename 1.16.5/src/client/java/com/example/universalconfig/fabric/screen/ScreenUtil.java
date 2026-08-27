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
import net.minecraft.client.util.Window;
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
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.MutableText;

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

    static MutableText literal(String value) {
        return new LiteralText(value == null ? "" : value);
    }

    static MutableText translatable(String key, Object... args) {
        return new TranslatableText(key, args);
    }

    static MutableText empty() {
        return new LiteralText("");
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, Text text,
                                   int x, int y, int color) {
        DrawableHelper.drawTextWithShadow(matrices, renderer, text, x, y, color);
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, OrderedText text,
                                   int x, int y, int color) {
        renderer.drawWithShadow(matrices, text, x, y, color);
    }

    static void drawTextWithShadow(MatrixStack matrices, TextRenderer renderer, String text,
                                   int x, int y, int color) {
        DrawableHelper.drawStringWithShadow(matrices, renderer, text == null ? "" : text, x, y, color);
    }

    static void drawCenteredTextWithShadow(MatrixStack matrices, TextRenderer renderer, Text text,
                                           int centerX, int y, int color) {
        renderer.drawWithShadow(matrices, text,
                centerX - renderer.getWidth(text) / 2.0F, y, color);
    }

    static void fill(MatrixStack matrices, int left, int top, int right, int bottom, int color) {
        DrawableHelper.fill(matrices, left, top, right, bottom, color);
    }

    static void enableScissor(int left, int top, int right, int bottom) {
        Window window = MinecraftClient.getInstance().getWindow();
        double scale = window.getScaleFactor();
        int framebufferLeft = (int) Math.floor(left * scale);
        int framebufferTop = (int) Math.floor(window.getFramebufferHeight() - bottom * scale);
        int framebufferWidth = (int) Math.ceil((right - left) * scale);
        int framebufferHeight = (int) Math.ceil((bottom - top) * scale);
        RenderSystem.enableScissor(framebufferLeft, framebufferTop, framebufferWidth, framebufferHeight);
    }

    static void disableScissor() {
        RenderSystem.disableScissor();
    }

    static void drawItem(MatrixStack matrices, ItemStack stack, int x, int y) {
        drawItem(matrices, stack, x, y, 1.0F);
    }

    static void drawItem(MatrixStack matrices, ItemStack stack, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 0.0F);
        RenderSystem.scalef(scale, scale, 1.0F);
        try {
            client.getItemRenderer().renderGuiItemIcon(stack, 0, 0);
        } finally {
            RenderSystem.popMatrix();
        }
    }

    /**
     * Provides the 1.20-style builder call shape on 1.19.2, whose ButtonWidget
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
        private boolean actionButton;
        private boolean wideButton;

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

        LegacyButtonBuilder actionButton() {
            this.actionButton = true;
            return this;
        }

        LegacyButtonBuilder wideButton() {
            this.wideButton = true;
            return this;
        }

        ButtonWidget build() {
            return actionButton
                    ? new VanillaButtonWidget(x, y + 2, width, 20, message, onPress)
                    : wideButton
                    ? new WideButtonWidget(x, y, width, height, message, onPress)
                    : new VanillaButtonWidget(x, y, width, height, message, onPress);
        }
    }

    /**
     * Keeps the 1.19.2 ButtonWidget contract while avoiding a full 20px widget texture stretch when a screen uses
     * a 24px button. Newer Minecraft versions render the same vanilla texture as a nine-slice, so the top and bottom
     * edges stay crisp and the middle section absorbs the extra height.
     */
    private static final class VanillaButtonWidget extends ButtonWidget {
        private VanillaButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
            // Keep all standard controls on the 1.16.5 vanilla rendering path.
            // Its ButtonWidget helper owns the correct texture binding and UVs.
            super.renderButton(context, mouseX, mouseY, delta);
        }

    }

    /**
     * The 1.16.5 vanilla renderer samples both halves of widgets.png using the
     * widget width. That produces a negative U coordinate for the full-width
     * footer button. Keep the vanilla 20px row and state selection, but draw it
     * as horizontal slices so the texture never samples outside its 200px row.
     */
    private static final class WideButtonWidget extends ButtonWidget {
        private static final int TEXTURE_WIDTH = 256;
        private static final int TEXTURE_HEIGHT = 256;
        private static final int BUTTON_TEXTURE_WIDTH = 200;
        private static final int SLICE_WIDTH = 4;
        private static final int BUTTON_TEXTURE_TOP = 46;

        private WideButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress);
        }

        @Override
        public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
            MinecraftClient client = MinecraftClient.getInstance();
            TextRenderer renderer = client.textRenderer;
            client.getTextureManager().bindTexture(WIDGETS_TEXTURE);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
            int textureY = BUTTON_TEXTURE_TOP + getYImage(isHovered()) * 20;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();

            if (width <= SLICE_WIDTH * 2) {
                super.renderButton(context, mouseX, mouseY, delta);
                return;
            }

            int middleWidth = width - SLICE_WIDTH * 2;
            DrawableHelper.drawTexture(context, x, y, SLICE_WIDTH, height,
                    0, textureY, SLICE_WIDTH, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            DrawableHelper.drawTexture(context, x + SLICE_WIDTH, y, middleWidth, height,
                    SLICE_WIDTH, textureY, BUTTON_TEXTURE_WIDTH - SLICE_WIDTH * 2, height,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
            DrawableHelper.drawTexture(context, x + width - SLICE_WIDTH, y, SLICE_WIDTH, height,
                    BUTTON_TEXTURE_WIDTH - SLICE_WIDTH, textureY, SLICE_WIDTH, height,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);

            int color = active ? 0xFFFFFF : 0xA0A0A0;
            DrawableHelper.drawCenteredText(context, renderer, getMessage(), x + width / 2,
                    y + (height - 8) / 2, color | ((int) (alpha * 255.0F) << 24));
        }
    }

    static Text errorText(Exception ex) {
        if (ex instanceof UniversalConfigException && ((UniversalConfigException) ex).translationKey() != null) {
            UniversalConfigException ucex = (UniversalConfigException) ex;
            Object[] args = ucex.translationArgs();
            return args == null || args.length == 0
                    ? ScreenUtil.translatable(ucex.translationKey())
                    : ScreenUtil.translatable(ucex.translationKey(), args);
        }

        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return ScreenUtil.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
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
