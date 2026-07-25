package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.CurrentProcessRestartService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ApplyScheduledScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_HEIGHT = 170;
    private static final int ERROR_PANEL_HEIGHT = 150;
    private static final int STACKED_PANEL_HEIGHT = 198;
    private static final int PANEL_COLOR = 0xE6101010;
    private static final int PANEL_BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFD0D0D0;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;
    private final boolean restartError;
    private boolean restarting;

    public ApplyScheduledScreen(Screen parent) {
        this(parent, false);
    }

    private ApplyScheduledScreen(Screen parent, boolean restartError) {
        super(Text.translatable(restartError
                ? "screen.universal_config.restart_failed_title"
                : "screen.universal_config.apply_scheduled_title"));
        this.parent = parent;
        this.restartError = restartError;
    }

    @Override
    protected void init() {
        int panelTop = panelTop();
        if (restartError) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.ok"), button -> close())
                    .dimensions(width / 2 - 50, panelTop + ERROR_PANEL_HEIGHT - 34, 100, 20)
                    .build());
            return;
        }

        Text laterLabel = Text.translatable("screen.universal_config.apply_scheduled_later");
        Text restartLabel = Text.translatable("screen.universal_config.restart_now");
        int buttonWidth = actionButtonWidth(laterLabel, restartLabel);
        if (stackButtons(buttonWidth)) {
            int buttonY = panelTop + STACKED_PANEL_HEIGHT - 54;
            addDrawableChild(ButtonWidget.builder(laterLabel, button -> close())
                    .dimensions(width / 2 - buttonWidth / 2, buttonY, buttonWidth, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(restartLabel, button -> restartMinecraft())
                    .dimensions(width / 2 - buttonWidth / 2, buttonY + 28, buttonWidth, 20)
                    .build());
        } else {
            int buttonY = panelTop + PANEL_HEIGHT - 34;
            int totalWidth = buttonWidth * 2 + BUTTON_GAP;
            int left = width / 2 - totalWidth / 2;
            addDrawableChild(ButtonWidget.builder(laterLabel, button -> close())
                    .dimensions(left, buttonY, buttonWidth, 20)
                    .build());
            addDrawableChild(ButtonWidget.builder(restartLabel, button -> restartMinecraft())
                    .dimensions(left + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, 20)
                    .build());
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        int panelLeft = panelLeft();
        int panelTop = panelTop();
        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        context.fill(panelLeft, panelTop, panelRight, panelBottom, PANEL_COLOR);
        context.fill(panelLeft, panelTop, panelRight, panelTop + 1, PANEL_BORDER_COLOR);
        context.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, PANEL_BORDER_COLOR);
        context.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, PANEL_BORDER_COLOR);
        context.fill(panelRight - 1, panelTop, panelRight, panelBottom, PANEL_BORDER_COLOR);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 16, TEXT_COLOR);
        int textY = panelTop + 48;
        String firstLineKey = restartError
                ? "screen.universal_config.restart_failed_line1"
                : "screen.universal_config.apply_scheduled_line1";
        String secondLineKey = restartError
                ? "screen.universal_config.restart_failed_line2"
                : "screen.universal_config.apply_scheduled_line2";
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(firstLineKey), width / 2, textY, MUTED_TEXT_COLOR);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(secondLineKey), width / 2, textY + 18, MUTED_TEXT_COLOR);

        super.render(context, mouseX, mouseY, delta);
    }

    private void restartMinecraft() {
        if (restarting) {
            return;
        }
        restarting = true;
        Path pendingPath = UniversalConfigPaths.pendingImportFile(ScreenUtil.instancePath());
        try {
            if (!Files.isRegularFile(pendingPath)) {
                throw new IllegalStateException("Pending import file is missing");
            }
            ProfileService service = ScreenUtil.service();
            PendingImport pending = service.readPendingImport(ScreenUtil.instancePath());
            if (pending == null) {
                throw new IllegalStateException("Pending import data is missing");
            }
            CurrentProcessRestartService.scheduleRestartAfterCurrentProcessExit();
            FileOperationLogger.info("RESTART_AFTER_SCHEDULE", pendingPath, "restart scheduled");
            client.scheduleStop();
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("RESTART_AFTER_SCHEDULE", pendingPath, "failed", ex);
            restarting = false;
            client.setScreen(new ApplyScheduledScreen(this, true));
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN * 2));
    }

    private int panelHeight() {
        if (restartError) {
            return ERROR_PANEL_HEIGHT;
        }
        return stackButtons(actionButtonWidth(
                Text.translatable("screen.universal_config.apply_scheduled_later"),
                Text.translatable("screen.universal_config.restart_now")))
                ? STACKED_PANEL_HEIGHT : PANEL_HEIGHT;
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(PANEL_MARGIN, (height - panelHeight()) / 2);
    }

    private int actionButtonWidth(Text first, Text second) {
        int measured = Math.max(textRenderer.getWidth(first), textRenderer.getWidth(second)) + 20;
        return Math.max(104, Math.min(150, measured));
    }

    private boolean stackButtons(int buttonWidth) {
        return panelWidth() < buttonWidth * 2 + BUTTON_GAP + 24;
    }
}
