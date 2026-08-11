package com.example.universalconfig.forgelegacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Adds a responsive lower-left main-menu entry while keeping Forge's credit area unobstructed. */
public final class LegacyTitleButtonHandler {
    private static final int BUTTON_ID = 0x5543;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof GuiMainMenu) {
            event.getButtonList().add(new TitleIconButton(BUTTON_ID, 4,
                    Math.max(4, event.getGui().height - 70)));
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getGui() instanceof GuiMainMenu && event.getButton() instanceof TitleIconButton
                && event.getButton().id == BUTTON_ID) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen(new LegacyScreens.ProfileList(event.getGui()));
        }
    }

    private static final class TitleIconButton extends GuiButton {
        private static final ResourceLocation ICON = new ResourceLocation(
                UniversalConfigLegacyMod.MOD_ID, "title_screen_button.png");

        private TitleIconButton(int id, int x, int y) {
            super(id, x, y, 20, 20, "");
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;
            super.drawButton(minecraft, mouseX, mouseY, partialTicks);
            minecraft.getTextureManager().bindTexture(ICON);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Gui.drawScaledCustomSizeModalRect(x + 2, y + 2,
                    0.0F, 0.0F, 128, 128, 16, 16, 128.0F, 128.0F);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }
}
