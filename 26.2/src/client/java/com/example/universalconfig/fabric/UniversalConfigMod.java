package com.example.universalconfig.fabric;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.fabric.screen.ProfileListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class UniversalConfigMod implements ClientModInitializer {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final Identifier TITLE_SCREEN_BUTTON_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "title_screen_button.png");
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    private static final int TITLE_SCREEN_SYSTEM_TEXT_BOTTOM_OFFSET = 10;
    private static final int TITLE_SCREEN_SYSTEM_TEXT_GAP = 4;
    // title_screen_button.png はボタン専用の15x15画像を使用する。drawTexture には
    // テクスチャ全体のピクセル幅・高さを渡す必要があるため、画像差し替え時はここも一致させる。
    // FabricのGuiGraphicsExtractor#drawTexture は (u,v)-(uRegion,vRegion) をテクスチャ全体で正規化するため、
    // 実寸を正しく指定しないと画像の左上一部分しか描画されない。
    private static final int TITLE_SCREEN_TEXTURE_WIDTH = 15;
    private static final int TITLE_SCREEN_TEXTURE_HEIGHT = 15;

    private boolean pendingImportLogged;

    @Override
    public void onInitializeClient() {
        // 設定画面はタイトル画面のボタンから開けるため、キーバインドは登録しない（Issue #35）。
        // Modの性質上ゲーム中に頻繁に開く用途ではなく、未割り当てのキーがキー設定一覧に
        // 残ると混乱を招く。過去に登録していたキーが options.txt に残っていても、Fabric は
        // 未登録の KeyMapping を単に無視するため、無害に放置される。
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                logPendingImportStateOnce(client);
                Component buttonLabel = Component.translatable("button.universal_config.open");
                // バニラの左下システム文字列の描画位置を基準に、最小限の間隔を確保する。
                // 文字ボタンではなくModアイコンだけを表示し、タイトル画面への視覚的な干渉を抑える。
                Screens.getWidgets(screen).removeIf(button -> button instanceof IconButton);
                IconButton openButton = new IconButton(
                        TITLE_SCREEN_BUTTON_MARGIN,
                        titleScreenButtonY(client, screen.height),
                        // setScreenAndShow renders synchronously in 26.2. The profile screen performs
                        // archive and settings reads while initializing, so install it for the next frame
                        // instead of re-entering rendering from the title-screen button callback.
                        button -> client.gui.setScreen(new ProfileListScreen(screen)),
                        buttonLabel
                );
                openButton.setTooltip(Tooltip.create(buttonLabel));
                Screens.getWidgets(screen).add(openButton);
            }
        });
    }

    private static int titleScreenButtonY(Minecraft client, int screenHeight) {
        int systemTextY = screenHeight - TITLE_SCREEN_SYSTEM_TEXT_BOTTOM_OFFSET;
        int reservedTextHeight = client.font.lineHeight + TITLE_SCREEN_SYSTEM_TEXT_GAP;
        return Math.max(
                TITLE_SCREEN_BUTTON_MARGIN,
                systemTextY - reservedTextHeight - TITLE_SCREEN_BUTTON_SIZE
        );
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    private static final class IconButton extends Button {
        private final Component narrationMessage;

        private IconButton(int x, int y, OnPress onPress, Component narrationMessage) {
            super(x, y, TITLE_SCREEN_BUTTON_SIZE, TITLE_SCREEN_BUTTON_SIZE,
                    Component.empty(), onPress, DEFAULT_NARRATION);
            this.narrationMessage = narrationMessage;
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", narrationMessage);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            // バニラのボタン背景とホバー状態をそのまま使い、タイトル画面の他ボタンと見た目を揃える。
            extractDefaultSprite(context);
            // 26.2のGuiGraphicsExtractorでは、非パイプライン版のblitは正規化UVを受け取る一方、
            // テクスチャ画像のピクセル範囲を明示するにはGUIテクスチャ用パイプラインを使う。
            // 旧シグネチャへ1.0のUVを渡すと、26.2のRenderState抽出時に画像が正しく解決されず、
            // ボタン背景だけが描画されるため、ソース範囲とテクスチャ実寸を明示する。
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            context.blit(RenderPipelines.GUI_TEXTURED, TITLE_SCREEN_BUTTON_TEXTURE,
                    getX() + TITLE_SCREEN_ICON_PADDING,
                    getY() + TITLE_SCREEN_ICON_PADDING,
                    0.0F, 0.0F,
                    iconSize, iconSize,
                    0, 0,
                    TITLE_SCREEN_TEXTURE_WIDTH, TITLE_SCREEN_TEXTURE_HEIGHT,
                    -1);
        }
    }

    private void logPendingImportStateOnce(Minecraft client) {
        if (pendingImportLogged) {
            return;
        }
        pendingImportLogged = true;
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(client.gameDirectory.toPath());
            FileOperationLogger.info("TITLE_SCREEN_READY", client.gameDirectory.toPath(),
                    "settingsRoot=" + settings.rootDirectory());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("TITLE_SCREEN_READY", client.gameDirectory.toPath(), "failed", ex);
        }
    }
}
