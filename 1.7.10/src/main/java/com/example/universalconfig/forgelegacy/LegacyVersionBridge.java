package com.example.universalconfig.forgelegacy;

import com.example.universalconfig.core.ProfileIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

final class LegacyVersionBridge {
    private LegacyVersionBridge() {
    }

    static FontRenderer font(Minecraft minecraft) {
        return minecraft.fontRenderer;
    }

    static void shutdown(Minecraft minecraft) {
        minecraft.shutdownMinecraftApplet();
    }

    static GuiTextField textField(int id, FontRenderer font, int x, int y, int width, int height) {
        return new GuiTextField(font, x, y, width, height);
    }

    static GuiButton button(int id, int x, int y, int width, int height, String label) {
        return new FittedButton(id, x, y, width, height, label);
    }

    static String minecraftVersion() {
        return cpw.mods.fml.common.Loader.MC_VERSION;
    }

    static void drawProfileIcon(Minecraft minecraft, String iconId, int x, int y, int size) {
        if (ProfileIcon.CHEST.equals(ProfileIcon.normalize(iconId))) {
            drawChestIcon(x, y, size);
            return;
        }
        ItemStack stack = iconStack(iconId);
        float scale = size / 16.0F;
        GL11.glPushMatrix();
        try {
            // 1.7.10's RenderItem assumes the caller prepared the same full-bright lightmap and normal rescaling
            // used by GuiContainer. Without these states, enlarged 3D block icons inherit a dark lightmap and their
            // scaled normals reduce the diffuse lighting further.
            RenderHelper.enableGUIStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glScalef(scale, scale, 1.0F);
            RenderItem.getInstance().renderItemAndEffectIntoGUI(
                    minecraft.fontRenderer, minecraft.renderEngine, stack,
                    Math.round(x / scale), Math.round(y / scale));
        } finally {
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
        }
    }

    private static ItemStack iconStack(String iconId) {
        String normalized = ProfileIcon.normalize(iconId);
        if (ProfileIcon.CRAFTING_TABLE.equals(normalized)) return new ItemStack(Blocks.crafting_table);
        if (ProfileIcon.BOOKSHELF.equals(normalized)) return new ItemStack(Blocks.bookshelf);
        if (ProfileIcon.COBBLESTONE.equals(normalized)) return new ItemStack(Blocks.cobblestone);
        if (ProfileIcon.TNT.equals(normalized)) return new ItemStack(Blocks.tnt);
        if (ProfileIcon.FURNACE.equals(normalized)) return new ItemStack(Blocks.furnace);
        if (ProfileIcon.DIAMOND_BLOCK.equals(normalized)) return new ItemStack(Blocks.diamond_block);
        return new ItemStack(Blocks.grass);
    }

    private static void drawChestIcon(int x, int y, int size) {
        int left = x + size * 2 / 16;
        int right = x + size * 14 / 16;
        int top = y + size * 3 / 16;
        int seam = y + size * 7 / 16;
        int bottom = y + size * 14 / 16;
        Gui.drawRect(left, top, right, bottom, 0xFF2B1607);
        Gui.drawRect(left + 1, top + 1, right - 1, seam, 0xFFB96D24);
        Gui.drawRect(left + 1, seam + 1, right - 1, bottom - 1, 0xFF8D4B17);
        Gui.drawRect(left + 1, top + 1, right - 1, top + 2, 0xFFD58B3B);
        Gui.drawRect(left, seam, right, seam + 1, 0xFF3A1E09);
        int latchWidth = Math.max(2, size * 3 / 16);
        int latchLeft = x + (size - latchWidth) / 2;
        Gui.drawRect(latchLeft, seam, latchLeft + latchWidth, seam + Math.max(3, size * 4 / 16), 0xFFC8C8A8);
        Gui.drawRect(latchLeft + 1, seam + 1, latchLeft + latchWidth - 1,
                seam + Math.max(2, size * 2 / 16), 0xFFF2E9B5);
    }

    private static final class FittedButton extends GuiButton {
        private FittedButton(int id, int x, int y, int width, int height, String label) {
            super(id, x, y, width, height, label);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            String label = displayString;
            if (width <= 400) {
                displayString = "";
                try {
                    super.drawButton(minecraft, mouseX, mouseY);
                } finally {
                    displayString = label;
                }
            } else {
                field_146123_n = mouseX >= xPosition && mouseY >= yPosition
                        && mouseX < xPosition + width && mouseY < yPosition + height;
                drawWideBackground(minecraft);
            }
            FontRenderer font = minecraft.fontRenderer;
            String fitted = fit(font, label, Math.max(0, width - 8));
            int color = !enabled ? 0xA0A0A0 : field_146123_n ? 0xFFFFA0 : 0xE0E0E0;
            font.drawStringWithShadow(fitted, xPosition + (width - font.getStringWidth(fitted)) / 2,
                    yPosition + (height - 8) / 2, color);
        }

        private void drawWideBackground(Minecraft minecraft) {
            minecraft.renderEngine.bindTexture(buttonTextures);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int textureY = 46 + getHoverState(field_146123_n) * 20;
            drawTexturedModalRect(xPosition, yPosition, 0, textureY, 4, height);
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(xPosition + 4, yPosition, 0.0F);
                GL11.glScalef((width - 8) / 192.0F, 1.0F, 1.0F);
                drawTexturedModalRect(0, 0, 4, textureY, 192, height);
            } finally {
                GL11.glPopMatrix();
            }
            drawTexturedModalRect(xPosition + width - 4, yPosition, 196, textureY, 4, height);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    private static String fit(FontRenderer font, String value, int maxWidth) {
        if (font.getStringWidth(value) <= maxWidth) return value;
        String suffix = "...";
        return font.trimStringToWidth(value, Math.max(0, maxWidth - font.getStringWidth(suffix))) + suffix;
    }
}
