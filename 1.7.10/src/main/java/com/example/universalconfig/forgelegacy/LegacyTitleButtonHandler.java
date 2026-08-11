package com.example.universalconfig.forgelegacy;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.opengl.GL11;

/** Adds the same icon entry point as newer ports without depending on version-specific menu internals. */
public final class LegacyTitleButtonHandler {
    private static final int BUTTON_ID = 0x5543;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiMainMenu) {
            // Forge renders several diagnostic credit lines at the lower-left; keep the button above that block.
            event.buttonList.add(new TitleIconButton(BUTTON_ID, 4, Math.max(4, event.gui.height - 70)));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui instanceof GuiMainMenu && event.button instanceof TitleIconButton
                && event.button.id == BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new LegacyScreens.ProfileList(event.gui));
        }
    }

    private static final class TitleIconButton extends GuiButton {
        private static final ResourceLocation ICON = new ResourceLocation(
                UniversalConfigLegacyMod.MOD_ID, "title_screen_button.png");

        private TitleIconButton(int id, int x, int y) {
            super(id, x, y, 20, 20, "");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            super.drawButton(minecraft, mouseX, mouseY);
            minecraft.renderEngine.bindTexture(ICON);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Tessellator tessellator = Tessellator.instance;
            double left = xPosition + 2;
            double top = yPosition + 2;
            double right = xPosition + 18;
            double bottom = yPosition + 18;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(left, bottom, zLevel, 0.0D, 1.0D);
            tessellator.addVertexWithUV(right, bottom, zLevel, 1.0D, 1.0D);
            tessellator.addVertexWithUV(right, top, zLevel, 1.0D, 0.0D);
            tessellator.addVertexWithUV(left, top, zLevel, 0.0D, 0.0D);
            tessellator.draw();
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
}
