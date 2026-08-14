package com.example.universalconfig.forgelegacy;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.FMLCommonHandler;
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
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_MARGIN = 4;
    // バニラの左下システム文字列（バージョン表記など）の描画位置を基準にする。
    // 1.7.10のGuiMainMenuは height-10 の位置に文字列を描画するため、その上に最小限の間隔を確保する。
    private static final int SYSTEM_TEXT_BOTTOM_OFFSET = 10;
    private static final int SYSTEM_TEXT_GAP = 2;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiMainMenu) {
            // 画面初期化のたびにイベントが呼ばれるため、既存ボタンを除去してから追加し直す。
            // これによりタイトル画面の再初期化でボタンが重複登録されるのを防ぐ（PR #43）。
            event.buttonList.removeIf(button -> button instanceof TitleIconButton);
            int y = titleButtonY(event.gui.height);
            event.buttonList.add(new TitleIconButton(BUTTON_ID, BUTTON_MARGIN, y));
        }
    }

    // バニラの左下システム文字列の上にボタンを配置する。
    // 固定の高さオフセットではなく文字列の描画位置から計算することで、画面サイズに応じた配置になる。
    private static int titleButtonY(int screenHeight) {
        int systemTextY = screenHeight - SYSTEM_TEXT_BOTTOM_OFFSET;
        // FMLはMinecraftの版表示に加えて左下へ複数行のブランド情報を描画する。
        // 実際の行数を予約し、Mod数やForge表記が増えてもアイコンを重ねない。
        int brandingLines = FMLCommonHandler.instance().getBrandings(true).size();
        int brandingStep = Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 1;
        // Forge places branding line 0 at systemTextY and each additional line one step above it. Anchoring the
        // button bottom to the top line's Y removes the artificial blank row without overlapping any glyphs.
        int topBrandingY = systemTextY - Math.max(0, brandingLines - 1) * brandingStep;
        return Math.max(BUTTON_MARGIN, topBrandingY - BUTTON_SIZE - SYSTEM_TEXT_GAP);
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
