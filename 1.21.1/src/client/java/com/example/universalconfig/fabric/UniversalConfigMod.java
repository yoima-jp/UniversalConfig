package com.example.universalconfig.fabric;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.fabric.screen.ProfileListScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class UniversalConfigMod implements ClientModInitializer {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final Identifier TITLE_SCREEN_BUTTON_TEXTURE = Identifier.of(MOD_ID, "title_screen_button.png");
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    private static final int TITLE_SCREEN_BOTTOM_TEXT_CLEARANCE = 14;

    private KeyBinding openKey;
    private boolean pendingImportLogged;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.universal_config.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.universal_config"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null || client.currentScreen instanceof TitleScreen) {
                    client.setScreen(new ProfileListScreen(client.currentScreen));
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen) {
                logPendingImportStateOnce(client);
                Text buttonLabel = Text.translatable("button.universal_config.open");
                // 左下にはバージョン表記、右下には著作権表記があるため、下端から14px空ける。
                // 文字ボタンではなくModアイコンだけを表示し、タイトル画面への視覚的な干渉を抑える。
                IconButtonWidget openButton = new IconButtonWidget(
                        TITLE_SCREEN_BUTTON_MARGIN,
                        scaledHeight - TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_BOTTOM_TEXT_CLEARANCE,
                        button -> client.setScreen(new ProfileListScreen(screen)),
                        buttonLabel
                );
                openButton.setTooltip(Tooltip.of(buttonLabel));
                Screens.getButtons(screen).add(openButton);
            }
        });
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
            // Modメタデータと同じ128px画像全体を縮小する。TexturedButtonWidgetでは左上20pxが
            // 等倍で切り抜かれる。操作領域より画像を小さくし、周囲のUIと詰まって見えない余白も残す。
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            context.drawTexture(TITLE_SCREEN_BUTTON_TEXTURE,
                    getX() + TITLE_SCREEN_ICON_PADDING,
                    getY() + TITLE_SCREEN_ICON_PADDING,
                    iconSize,
                    iconSize,
                    0.0F, 0.0F, 128, 128, 128, 128);
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
