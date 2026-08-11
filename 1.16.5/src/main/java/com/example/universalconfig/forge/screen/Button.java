package com.example.universalconfig.forge.screen;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.ITextComponent;

/**
 * 1.20で導入されたButton.Builder相当を1.19系へ提供する。
 * 画面コードのレイアウト計算を世代間で揃え、狭い画面での配置差を防ぐため、この層だけでAPI差を吸収する。
 */
class Button extends net.minecraft.client.gui.widget.button.Button {
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
        int state = getYImage(isHovered());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        // Vanilla 1.16 copies `width / 2` pixels from each half of a 200px source. Wider responsive buttons therefore
        // read outside the widget texture and split into disconnected bars. Stretch the two valid 100px halves instead.
        int leftWidth = width / 2;
        int rightWidth = width - leftWidth;
        int sourceY = 46 + state * 20;
        AbstractGui.blit(graphics, x, y, leftWidth, height,
                0.0F, sourceY, 100, 20, 256, 256);
        AbstractGui.blit(graphics, x + leftWidth, y, rightWidth, height,
                100.0F, sourceY, 100, 20, 256, 256);
        renderBg(graphics, minecraft, mouseX, mouseY);

        int textColor = getFGColor() | ((int) Math.ceil(alpha * 255.0F) << 24);
        AbstractGui.drawCenteredString(graphics, minecraft.font, getMessage(),
                x + width / 2, y + (height - 8) / 2, textColor);
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
