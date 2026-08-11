package com.example.universalconfig.forge.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 1.20で導入されたButton.Builder相当を1.19系へ提供する。
 * 画面コードのレイアウト計算を世代間で揃え、狭い画面での配置差を防ぐため、この層だけでAPI差を吸収する。
 */
class Button extends net.minecraft.client.gui.components.Button {
    protected Button(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void renderButton(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        int state = getYImage(isHoveredOrFocused());
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        int leftWidth = width / 2;
        int rightWidth = width - leftWidth;
        int sourceY = 46 + state * 20;
        GuiComponent.blit(graphics, x, y, leftWidth, height,
                0.0F, sourceY, 100, 20, 256, 256);
        GuiComponent.blit(graphics, x + leftWidth, y, rightWidth, height,
                100.0F, sourceY, 100, 20, 256, 256);
        renderBg(graphics, minecraft, mouseX, mouseY);
        int textColor = getFGColor() | (Mth.ceil(alpha * 255.0F) << 24);
        GuiComponent.drawCenteredString(graphics, minecraft.font, getMessage(),
                x + width / 2, y + (height - 8) / 2, textColor);
    }

    static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width;
        private int height;
        private java.util.function.Function<Button, ? extends Component> narrationFactory;

        private Builder(Component message, OnPress onPress) {
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

        Builder createNarration(java.util.function.Function<Button, ? extends Component> narrationFactory) {
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
        private final java.util.function.Function<Button, ? extends Component> narrationFactory;

        private NarratedButton(int x, int y, int width, int height, Component message, OnPress onPress,
                               java.util.function.Function<Button, ? extends Component> narrationFactory) {
            super(x, y, width, height, message, onPress);
            this.narrationFactory = narrationFactory;
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return narrationFactory.apply(this).copy();
        }
    }
}
