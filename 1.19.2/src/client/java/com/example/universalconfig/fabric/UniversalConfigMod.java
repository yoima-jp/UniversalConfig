package com.example.universalconfig.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.fabric.screen.ProfileListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class UniversalConfigMod implements ClientModInitializer {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final Identifier TITLE_SCREEN_BUTTON_TEXTURE = new Identifier(MOD_ID, "title_screen_button.png");
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    private static final int TITLE_SCREEN_SYSTEM_TEXT_BOTTOM_OFFSET = 10;
    private static final int TITLE_SCREEN_SYSTEM_TEXT_GAP = 4;
    // title_screen_button.png はボタン専用の15x15画像を使用する。drawTexture には
    // テクスチャ全体のピクセル幅・高さを渡す必要があるため、画像差し替え時はここも一致させる。
    // 1.19.2のDrawableHelper#drawTexture は (u,v)-(uRegion,vRegion) をテクスチャ全体で正規化するため、
    // 実寸を正しく指定しないと画像の左上一部分しか描画されない。
    private static final int TITLE_SCREEN_TEXTURE_WIDTH = 15;
    private static final int TITLE_SCREEN_TEXTURE_HEIGHT = 15;

    private boolean pendingImportLogged;

    @Override
    public void onInitializeClient() {
        // 設定画面はタイトル画面のボタンから開けるため、キーバインドは登録しない（Issue #35）。
        // Modの性質上ゲーム中に頻繁に開く用途ではなく、未割り当てのキーがキー設定一覧に
        // 残ると混乱を招く。過去に登録していたキーが options.txt に残っていても、Fabric は
        // 未登録の KeyBinding を単に無視するため、無害に放置される。
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                logPendingImportStateOnce(client);
                Text buttonLabel = Text.translatable("button.universal_config.open");
                // Vanillaのwidget構成に依存せず、画面端から共通marginで配置する。
                // 文字ボタンではなくModアイコンだけを表示し、タイトル画面への視覚的な干渉を抑える。
                Screens.getButtons(screen).removeIf(button -> button instanceof IconButtonWidget);
                IconButtonWidget openButton = new IconButtonWidget(
                        TITLE_SCREEN_BUTTON_MARGIN,
                        titleScreenButtonY(client, screen.height),
                        button -> client.setScreen(new ProfileListScreen(screen)),
                        buttonLabel
                );
                Screens.getButtons(screen).add(openButton);
            }
        });
    }

    private static int titleScreenButtonY(MinecraftClient client, int screenHeight) {
        int systemTextY = screenHeight - TITLE_SCREEN_SYSTEM_TEXT_BOTTOM_OFFSET;
        int reservedTextHeight = client.textRenderer.fontHeight + TITLE_SCREEN_SYSTEM_TEXT_GAP;
        return Math.max(
                TITLE_SCREEN_BUTTON_MARGIN,
                systemTextY - reservedTextHeight - TITLE_SCREEN_BUTTON_SIZE
        );
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private static final class IconButtonWidget extends ButtonWidget {
        private final Text narrationMessage;

        private IconButtonWidget(int x, int y, PressAction onPress, Text narrationMessage) {
            super(x, y, TITLE_SCREEN_BUTTON_SIZE, TITLE_SCREEN_BUTTON_SIZE,
                    Text.empty(), onPress);
            this.narrationMessage = narrationMessage;
        }

        @Override
        protected MutableText getNarrationMessage() {
            return Text.translatable("gui.narrate.button", narrationMessage);
        }

        @Override
        public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
            // バニラのボタン背景とホバー状態をそのまま使い、タイトル画面の他ボタンと見た目を揃える。
            super.renderButton(context, mouseX, mouseY, delta);
            // ボタン専用の15px画像全体を縮小描画する。TexturedButtonWidget では左上1枠が
            // 等倍で切り抜かれるため、本実装では drawTexture でテクスチャ全体をボタン内に収める。
            // 画像を差し替えた際は TITLE_SCREEN_TEXTURE_WIDTH/HEIGHT と実寸を一致させること。
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            MinecraftClient.getInstance().getTextureManager().bindTexture(TITLE_SCREEN_BUTTON_TEXTURE);
            RenderSystem.setShaderTexture(0, TITLE_SCREEN_BUTTON_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            DrawableHelper.drawTexture(context,
                    x + TITLE_SCREEN_ICON_PADDING,
                    y + TITLE_SCREEN_ICON_PADDING,
                    0.0F, 0.0F,
                    iconSize,
                    iconSize,
                    TITLE_SCREEN_TEXTURE_WIDTH,
                    TITLE_SCREEN_TEXTURE_HEIGHT);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void logPendingImportStateOnce(MinecraftClient client) {
        if (pendingImportLogged) {
            return;
        }
        pendingImportLogged = true;
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(client.runDirectory.toPath());
            FileOperationLogger.info("TITLE_SCREEN_READY", client.runDirectory.toPath(),
                    "settingsRoot=" + settings.rootDirectory());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("TITLE_SCREEN_READY", client.runDirectory.toPath(), "failed", ex);
        }
    }
}
