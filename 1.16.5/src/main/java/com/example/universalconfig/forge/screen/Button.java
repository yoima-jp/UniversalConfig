package com.example.universalconfig.forge.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextComponent;

/**
 * 1.20で導入されたButton.Builderとnine-slice描画相当を1.16系へ提供する。
 * 画面コードのレイアウトとボタンの縁・長文表示を世代間で揃えるため、この層だけでAPI差を吸収する。
 */
class Button extends net.minecraft.client.gui.widget.button.Button {
    private static final int SOURCE_WIDTH = 200;
    private static final int SOURCE_HEIGHT = 20;
    private static final int HORIZONTAL_BORDER = 20;
    private static final int VERTICAL_BORDER = 4;
    private static final int TEXT_MARGIN = 2;

    protected Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress) {
        super(x, y, width, height, message, onPress);
    }

    static Builder builder(ITextComponent message, IPressable onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void renderButton(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getTextureManager().bind(WIDGETS_LOCATION);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        // 1.20.1と同様、マウスだけでなくキーボードフォーカスもホバー表示にする。
        int state = getYImage(isHovered() || isFocused());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        int sourceY = 46 + state * 20;
        drawNineSlicedBackground(graphics, sourceY);
        renderBg(graphics, minecraft, mouseX, mouseY);

        int textColor = getFGColor() | ((int) Math.ceil(alpha * 255.0F) << 24);
        drawScrollingText(graphics, minecraft, textColor);
    }

    private void drawNineSlicedBackground(MatrixStack graphics, int sourceY) {
        int left = Math.min(HORIZONTAL_BORDER, width / 2);
        int right = Math.min(HORIZONTAL_BORDER, width / 2);
        int top = Math.min(VERTICAL_BORDER, height / 2);
        int bottom = Math.min(VERTICAL_BORDER, height / 2);
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;
        int sourceCenterWidth = SOURCE_WIDTH - HORIZONTAL_BORDER * 2;
        int sourceCenterHeight = SOURCE_HEIGHT - VERTICAL_BORDER * 2;

        blitTile(graphics, x, y, left, top, 0, sourceY, left, top);
        blitTiled(graphics, x + left, y, centerWidth, top,
                HORIZONTAL_BORDER, sourceY, sourceCenterWidth, top);
        blitTile(graphics, x + width - right, y, right, top,
                SOURCE_WIDTH - right, sourceY, right, top);
        blitTiled(graphics, x, y + top, left, centerHeight,
                0, sourceY + VERTICAL_BORDER, left, sourceCenterHeight);
        blitTiled(graphics, x + left, y + top, centerWidth, centerHeight,
                HORIZONTAL_BORDER, sourceY + VERTICAL_BORDER, sourceCenterWidth, sourceCenterHeight);
        blitTiled(graphics, x + width - right, y + top, right, centerHeight,
                SOURCE_WIDTH - right, sourceY + VERTICAL_BORDER, right, sourceCenterHeight);
        blitTile(graphics, x, y + height - bottom, left, bottom,
                0, sourceY + SOURCE_HEIGHT - bottom, left, bottom);
        blitTiled(graphics, x + left, y + height - bottom, centerWidth, bottom,
                HORIZONTAL_BORDER, sourceY + SOURCE_HEIGHT - bottom, sourceCenterWidth, bottom);
        blitTile(graphics, x + width - right, y + height - bottom, right, bottom,
                SOURCE_WIDTH - right, sourceY + SOURCE_HEIGHT - bottom, right, bottom);
    }

    private static void blitTiled(MatrixStack graphics, int destinationX, int destinationY,
                                  int destinationWidth, int destinationHeight,
                                  int sourceX, int sourceY, int tileWidth, int tileHeight) {
        for (int offsetY = 0; offsetY < destinationHeight; offsetY += tileHeight) {
            int drawHeight = Math.min(tileHeight, destinationHeight - offsetY);
            for (int offsetX = 0; offsetX < destinationWidth; offsetX += tileWidth) {
                int drawWidth = Math.min(tileWidth, destinationWidth - offsetX);
                blitTile(graphics, destinationX + offsetX, destinationY + offsetY,
                        drawWidth, drawHeight, sourceX, sourceY, drawWidth, drawHeight);
            }
        }
    }

    private static void blitTile(MatrixStack graphics, int destinationX, int destinationY,
                                 int destinationWidth, int destinationHeight,
                                 int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        if (destinationWidth <= 0 || destinationHeight <= 0) {
            return;
        }
        AbstractGui.blit(graphics, destinationX, destinationY, destinationWidth, destinationHeight,
                sourceX, sourceY, sourceWidth, sourceHeight, 256, 256);
    }

    private void drawScrollingText(MatrixStack graphics, Minecraft minecraft, int color) {
        int left = x + TEXT_MARGIN;
        int right = x + width - TEXT_MARGIN;
        int textY = y + (height - 9) / 2 + 1;
        int textWidth = minecraft.font.width(getMessage());
        int availableWidth = right - left;
        if (textWidth <= availableWidth) {
            AbstractGui.drawCenteredString(graphics, minecraft.font, getMessage(), x + width / 2, textY, color);
            return;
        }

        // 1.20.1と同じ往復式で長い翻訳をスクロールし、ボタン外へ文字を漏らさない。
        int overflow = textWidth - availableWidth;
        double duration = Math.max(overflow * 0.5D, 3.0D);
        double time = System.currentTimeMillis() / 1000.0D;
        double progress = Math.sin((Math.PI / 2.0D) * Math.cos(2.0D * Math.PI * time / duration)) / 2.0D + 0.5D;
        int offset = (int) (progress * overflow);
        ScreenUtil.enableScissor(left, y, right, y + height);
        try {
            AbstractGui.drawString(graphics, minecraft.font, getMessage(), left - offset, textY, color);
        } finally {
            ScreenUtil.disableScissor();
        }
    }

    static final class Builder {
        private final ITextComponent message;
        private final IPressable onPress;
        private int x;
        private int y;
        private int width;
        private int height;
        private java.util.function.Function<Button, ? extends ITextComponent> narrationFactory;

        private Builder(ITextComponent message, IPressable onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        Builder createNarration(java.util.function.Function<Button, ? extends ITextComponent> narrationFactory) {
            this.narrationFactory = narrationFactory;
            return this;
        }

        Button build() {
            if (narrationFactory == null) {
                return new Button(x, y, width, height, message, onPress);
            }
            return new NarratedButton(x, y, width, height, message, onPress, narrationFactory);
        }
    }

    private static final class NarratedButton extends Button {
        private final java.util.function.Function<Button, ? extends ITextComponent> narrationFactory;

        private NarratedButton(int x, int y, int width, int height, ITextComponent message, IPressable onPress,
                               java.util.function.Function<Button, ? extends ITextComponent> narrationFactory) {
            super(x, y, width, height, message, onPress);
            this.narrationFactory = narrationFactory;
        }

        @Override
        protected net.minecraft.util.text.IFormattableTextComponent createNarrationMessage() {
            return narrationFactory.apply(this).copy();
        }
    }
}
