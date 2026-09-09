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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class UniversalConfigMod implements ClientModInitializer {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final Identifier TITLE_SCREEN_BUTTON_TEXTURE = Identifier.of(MOD_ID, "title_screen_button.png");
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    // title_screen_button.png はボタン専用の15x15画像を使用する。drawTexture には
    // テクスチャ全体のピクセル幅・高さを渡す必要があるため、画像差し替え時はここも一致させる。
    // FabricのDrawContext#drawTexture は (u,v)-(uRegion,vRegion) をテクスチャ全体で正規化するため、
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
                // Options行の実widget geometryを基準に、全解像度で同じ位置へ配置する。
                // 文字ボタンではなくModアイコンだけを表示し、タイトル画面への視覚的な干渉を抑える。
                Screens.getButtons(screen).removeIf(button -> button instanceof IconButtonWidget);
                ClickableWidget optionsAnchor = optionsButton(screen);
                if (optionsAnchor == null) {
                    return;
                }
                IconButtonWidget openButton = new IconButtonWidget(
                        titleScreenButtonX(optionsAnchor),
                        titleScreenButtonY(optionsAnchor),
                        button -> client.setScreen(new ProfileListScreen(screen)),
                        buttonLabel
                );
                openButton.setTooltip(Tooltip.of(buttonLabel));
                Screens.getButtons(screen).add(openButton);
            }
        });
    }


    private static int titleScreenButtonX(ClickableWidget optionsAnchor) {
        return Math.max(TITLE_SCREEN_BUTTON_MARGIN,
                optionsAnchor.getX() - TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_BUTTON_MARGIN);
    }

    private static int titleScreenButtonY(ClickableWidget optionsAnchor) {
        return optionsAnchor.getY()
                + Math.max(0, (optionsAnchor.getHeight() - TITLE_SCREEN_BUTTON_SIZE) / 2);
    }
    private static ClickableWidget optionsButton(Screen screen) {
        ClickableWidget result = null;
        for (ClickableWidget candidate : Screens.getButtons(screen)) {
            if (!(candidate instanceof ButtonWidget)
                    || candidate.getWidth() <= TITLE_SCREEN_BUTTON_SIZE
                    || !hasMatchingRowButton(screen, candidate)) {
                continue;
            }
            if (result == null || candidate.getX() < result.getX()) {
                result = candidate;
            }
        }
        return result;
    }

    private static boolean hasMatchingRowButton(Screen screen, ClickableWidget candidate) {
        for (ClickableWidget peer : Screens.getButtons(screen)) {
            if (peer != candidate
                    && peer.getX() > candidate.getX()
                    && peer.getY() == candidate.getY()
                    && peer.getWidth() == candidate.getWidth()
                    && peer.getHeight() == candidate.getHeight()) {
                return true;
            }
        }
        return false;
    }
    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private static final class IconButtonWidget extends ButtonWidget {
        private final Text narrationMessage;

        private IconButtonWidget(int x, int y, PressAction onPress, Text narrationMessage) {
            super(x, y, TITLE_SCREEN_BUTTON_SIZE, TITLE_SCREEN_BUTTON_SIZE,
                    Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
            this.narrationMessage = narrationMessage;
        }

        @Override
        protected MutableText getNarrationMessage() {
            return Text.translatable("gui.narrate.button", narrationMessage);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            // バニラのボタン背景とホバー状態をそのまま使い、タイトル画面の他ボタンと見た目を揃える。
            super.renderWidget(context, mouseX, mouseY, delta);
            // ボタン専用の15px画像全体を縮小描画する。TexturedButtonWidget では左上1枠が
            // 等倍で切り抜かれるため、本実装では drawTexture でテクスチャ全体をボタン内に収める。
            // 画像を差し替えた際は TITLE_SCREEN_TEXTURE_WIDTH/HEIGHT と実寸を一致させること。
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            context.drawTexture(TITLE_SCREEN_BUTTON_TEXTURE,
                    getX() + TITLE_SCREEN_ICON_PADDING,
                    getY() + TITLE_SCREEN_ICON_PADDING,
                    iconSize,
                    iconSize,
                    0.0F, 0.0F,
                    TITLE_SCREEN_TEXTURE_WIDTH, TITLE_SCREEN_TEXTURE_HEIGHT,
                    TITLE_SCREEN_TEXTURE_WIDTH, TITLE_SCREEN_TEXTURE_HEIGHT);
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
