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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
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
    // title_screen_button.png はボタン専用の15x15画像を使用する。drawTexture には
    // テクスチャ全体のピクセル幅・高さを渡す必要があるため、画像差し替え時はここも一致させる。
    // GuiGraphicsExtractor#blit は u/v の後に描画サイズとテクスチャ全体の実寸を受け取るため、
    // 画像差し替え時はテクスチャ全体の幅・高さも一致させる。
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
                // Vanillaのwidget構成に依存せず、画面端から共通marginで配置する。
                // 文字ボタンではなくModアイコンだけを表示し、タイトル画面への視覚的な干渉を抑える。
                Screens.getWidgets(screen).removeIf(button -> button instanceof IconButton);
                IconButton openButton = new IconButton(
                        TITLE_SCREEN_BUTTON_MARGIN,
                        screen.height - TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_BUTTON_MARGIN,
                        button -> scheduleScreen(client, new ProfileListScreen(screen)),
                        buttonLabel
                );
                openButton.setTooltip(Tooltip.create(buttonLabel));
                Screens.getWidgets(screen).add(openButton);
            }
        });
    }

    /**
     * Screen callbacks run while the current screen is processing input. Queue
     * the replacement so Fabric's per-screen event state is fully unwound
     * before the next screen is initialized.
     */
    public static void scheduleScreen(Minecraft client, Screen screen) {
        client.schedule(() -> client.gui.setScreen(screen));
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
            // アイコンもWidget自身の抽出処理に追加する。画面全体のafterExtractイベントに依存すると、
            // 画面遷移やWidgetレイヤーの順序変更で、ボタン背景だけが残る可能性がある。
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            context.blit(RenderPipelines.GUI_TEXTURED, TITLE_SCREEN_BUTTON_TEXTURE,
                    getX() + TITLE_SCREEN_ICON_PADDING,
                    getY() + TITLE_SCREEN_ICON_PADDING,
                    0.0F, 0.0F,
                    iconSize, iconSize,
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
