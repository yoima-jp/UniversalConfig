package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.forge.ForgeRestartService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ApplyScheduledScreen extends Screen {
    private enum State {
        SCHEDULED,
        RESTART_FAILED,
        PENDING_INVALID
    }

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_MARGIN = 12;
    private static final int SCHEDULED_PANEL_HEIGHT = 166;
    private static final int SCHEDULED_STACKED_PANEL_HEIGHT = 194;
    private static final int ERROR_PANEL_HEIGHT = 160;
    private static final int ERROR_STACKED_PANEL_HEIGHT = 186;
    private static final int PANEL_COLOR = 0xE6101010;
    private static final int PANEL_BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFD0D0D0;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 8;
    private static final int PANEL_BOTTOM_PADDING = 14;
    private static final int BOTTOM_BUTTON_OFFSET = BUTTON_HEIGHT + PANEL_BOTTOM_PADDING;

    private final Screen parent;
    private final State state;
    private boolean restarting;
    private boolean canceling;
    private boolean cancelError;
    private boolean exiting;

    public ApplyScheduledScreen(Screen parent) {
        this(parent, State.SCHEDULED);
    }

    public static ApplyScheduledScreen restartFailed(Screen parent) {
        return new ApplyScheduledScreen(parent, State.RESTART_FAILED);
    }

    private ApplyScheduledScreen(Screen parent, State state) {
        super(Component.translatable(titleKey(state)));
        this.parent = parent;
        this.state = state;
    }

    @Override
    protected void init() {
        if (state != State.SCHEDULED) {
            initErrorButtons();
            return;
        }
        initScheduledButtons();
    }

    // Issue #25: restore an explicit cancel action on the scheduled screen.
    // Cancel deletes only this instance's pending import reservation; profiles and current settings are untouched.
    // The reservation is recreatable from the profile list, so no confirmation dialog is needed.
    private void initScheduledButtons() {
        int panelTop = panelTop();
        Component laterLabel = Component.translatable("screen.universal_config.apply_scheduled_later");
        Component restartLabel = Component.translatable("screen.universal_config.restart_now");
        Component cancelLabel = Component.translatable("screen.universal_config.apply_scheduled_cancel");
        int buttonWidth = scheduledButtonWidth();
        boolean stack = stackButtons(buttonWidth);
        int panelHeight = stack ? SCHEDULED_STACKED_PANEL_HEIGHT : SCHEDULED_PANEL_HEIGHT;
        int bottomRowY = panelTop + panelHeight - BOTTOM_BUTTON_OFFSET;
        int actionRowY = bottomRowY - BUTTON_HEIGHT - ROW_GAP;
        if (stack) {
            int firstActionY = actionRowY - BUTTON_HEIGHT - ROW_GAP;
            addRenderableWidget(Button.builder(laterLabel, button -> onClose())
                    .bounds(width / 2 - buttonWidth / 2, firstActionY, buttonWidth, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(restartLabel, button -> restartMinecraft())
                    .bounds(width / 2 - buttonWidth / 2, actionRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
        } else {
            int totalActionWidth = buttonWidth * 2 + BUTTON_GAP;
            int actionLeft = width / 2 - totalActionWidth / 2;
            addRenderableWidget(Button.builder(laterLabel, button -> onClose())
                    .bounds(actionLeft, actionRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(restartLabel, button -> restartMinecraft())
                    .bounds(actionLeft + buttonWidth + BUTTON_GAP, actionRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
        }
        addRenderableWidget(Button.builder(cancelLabel, button -> cancelPendingApply())
                .bounds(width / 2 - buttonWidth / 2, bottomRowY, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    // Issue #25: when automatic restart cannot be scheduled, offer an explicit quit action.
    // CurrentProcessRestartService cleans artifacts belonging to a failed scheduling attempt. This screen only quits
    // and preserves the pending reservation, avoiding deletion of another process's active restart artifacts.
    private void initErrorButtons() {
        int panelTop = panelTop();
        Component backLabel = Component.translatable("screen.universal_config.back");
        if (state == State.PENDING_INVALID) {
            int buttonWidth = measureButton(backLabel);
            int buttonY = panelTop + ERROR_PANEL_HEIGHT - BOTTOM_BUTTON_OFFSET;
            addRenderableWidget(Button.builder(backLabel, button -> onClose())
                    .bounds(width / 2 - buttonWidth / 2, buttonY, buttonWidth, BUTTON_HEIGHT)
                    .build());
            return;
        }

        Component quitLabel = Component.translatable("screen.universal_config.quit");
        int buttonWidth = errorButtonWidth();
        boolean stack = stackButtons(buttonWidth);
        int panelHeight = stack ? ERROR_STACKED_PANEL_HEIGHT : ERROR_PANEL_HEIGHT;
        int bottomRowY = panelTop + panelHeight - BOTTOM_BUTTON_OFFSET;
        if (stack) {
            int topRowY = bottomRowY - BUTTON_HEIGHT - ROW_GAP;
            addRenderableWidget(Button.builder(backLabel, button -> onClose())
                    .bounds(width / 2 - buttonWidth / 2, topRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(quitLabel, button -> quitMinecraft())
                    .bounds(width / 2 - buttonWidth / 2, bottomRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
        } else {
            int totalWidth = buttonWidth * 2 + BUTTON_GAP;
            int left = width / 2 - totalWidth / 2;
            addRenderableWidget(Button.builder(backLabel, button -> onClose())
                    .bounds(left, bottomRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
            addRenderableWidget(Button.builder(quitLabel, button -> quitMinecraft())
                    .bounds(left + buttonWidth + BUTTON_GAP, bottomRowY, buttonWidth, BUTTON_HEIGHT)
                    .build());
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

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

        context.centeredText(font, title, width / 2, panelTop + 16, TEXT_COLOR);
        int textY = panelTop + 48;
        String firstLineKey = state == State.RESTART_FAILED
                ? "screen.universal_config.restart_failed_line1"
                : state == State.PENDING_INVALID
                ? "screen.universal_config.pending_invalid_line1"
                : "screen.universal_config.apply_scheduled_line1";
        String secondLineKey = state == State.RESTART_FAILED
                ? "screen.universal_config.restart_failed_line2"
                : state == State.PENDING_INVALID
                ? "screen.universal_config.pending_invalid_line2"
                : "screen.universal_config.apply_scheduled_line2";
        context.centeredText(font, Component.translatable(firstLineKey), width / 2, textY, MUTED_TEXT_COLOR);
        context.centeredText(font, Component.translatable(secondLineKey), width / 2, textY + 18, MUTED_TEXT_COLOR);
        if (state == State.RESTART_FAILED) {
            // Translated text length varies, so wrap within the panel instead of allowing it to cross the border.
            drawWrappedCenteredText(context, Component.translatable("screen.universal_config.restart_failed_line3"),
                    textY + 36, MUTED_TEXT_COLOR);
        } else if (cancelError) {
            drawWrappedCenteredText(context, Component.translatable("screen.universal_config.cancel_failed"),
                    textY + 36, 0xFFFF7777);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);
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
        } catch (UniversalConfigException | RuntimeException ex) {
            // A missing or malformed reservation cannot be promised for the next launch. Keep this failure separate
            // from launcher detection so the user is sent back to create a new reservation instead of quitting.
            FileOperationLogger.failure("RESTART_AFTER_SCHEDULE", pendingPath, "pending import invalid", ex);
            restarting = false;
            minecraft.setScreen(new ApplyScheduledScreen(parent, State.PENDING_INVALID));
            return;
        }

        try {
            ForgeRestartService.scheduleRestartAfterCurrentProcessExit();
            FileOperationLogger.info("RESTART_AFTER_SCHEDULE", pendingPath, "restart scheduled");
            minecraft.stop();
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("RESTART_AFTER_SCHEDULE", pendingPath, "failed", ex);
            restarting = false;
            minecraft.setScreen(new ApplyScheduledScreen(this, State.RESTART_FAILED));
        }
    }

    // Issue #25: cancel the scheduled apply. Only the pending import reservation for this instance is removed;
    // profiles and the current Minecraft settings are never touched. Success/failure is logged by clearPendingImport.
    // The canceling flag prevents double execution from repeated clicks or screen reinitialization.
    private void cancelPendingApply() {
        if (canceling) {
            return;
        }
        canceling = true;
        cancelError = false;
        Path instancePath = ScreenUtil.instancePath();
        ProfileService service;
        try {
            service = ScreenUtil.service();
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("CANCEL_PENDING_IMPORT",
                    UniversalConfigPaths.pendingImportFile(instancePath), "service initialization failed", ex);
            canceling = false;
            cancelError = true;
            return;
        }

        try {
            service.clearPendingImport(instancePath);
            // Return to the parent screen (typically the profile list) where the pending banner is now gone.
            onClose();
        } catch (UniversalConfigException ex) {
            // clearPendingImport records the file-operation failure at the core boundary; logging it again here would
            // duplicate the same event. This layer only keeps the screen actionable for a retry.
            canceling = false;
            cancelError = true;
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("CANCEL_PENDING_IMPORT",
                    UniversalConfigPaths.pendingImportFile(instancePath), "unexpected screen failure", ex);
            canceling = false;
            cancelError = true;
        }
    }

    // Issue #25: quit Minecraft after a restart could not be scheduled. The pending apply reservation is intentionally
    // preserved so the next manual launch applies it. CurrentProcessRestartService already removes the ready marker
    // for every failed scheduling path, so this screen must not sweep files that another process may still own.
    private void quitMinecraft() {
        if (exiting) {
            return;
        }
        exiting = true;
        Path instancePath = ScreenUtil.instancePath();
        FileOperationLogger.info("QUIT_AFTER_RESTART_FAILED",
                UniversalConfigPaths.pendingImportFile(instancePath), "quit requested; pending import preserved");
        minecraft.stop();
    }

    private void drawWrappedCenteredText(GuiGraphicsExtractor context, Component message, int startY, int color) {
        int maxTextWidth = Math.max(1, panelWidth() - PANEL_MARGIN * 2);
        List<FormattedCharSequence> lines = font.split(message, maxTextWidth);
        int lineY = startY;
        for (FormattedCharSequence line : lines) {
            int lineX = width / 2 - font.width(line) / 2;
            context.text(font, line, lineX, lineY, color);
            lineY += 10;
        }
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN * 2));
    }

    private int panelHeight() {
        if (state == State.PENDING_INVALID) {
            return ERROR_PANEL_HEIGHT;
        }
        if (state == State.RESTART_FAILED) {
            return stackButtons(errorButtonWidth()) ? ERROR_STACKED_PANEL_HEIGHT : ERROR_PANEL_HEIGHT;
        }
        return stackButtons(scheduledButtonWidth()) ? SCHEDULED_STACKED_PANEL_HEIGHT : SCHEDULED_PANEL_HEIGHT;
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return Math.max(PANEL_MARGIN, (height - panelHeight()) / 2);
    }

    private int measureButton(Component label) {
        int measured = font.width(label) + 20;
        return Math.max(104, Math.min(150, measured));
    }

    private int pairButtonWidth(Component first, Component second) {
        return Math.max(measureButton(first), measureButton(second));
    }

    private int scheduledButtonWidth() {
        int action = pairButtonWidth(
                Component.translatable("screen.universal_config.apply_scheduled_later"),
                Component.translatable("screen.universal_config.restart_now"));
        int cancel = measureButton(Component.translatable("screen.universal_config.apply_scheduled_cancel"));
        return Math.max(action, cancel);
    }

    private int errorButtonWidth() {
        return pairButtonWidth(
                Component.translatable("screen.universal_config.back"),
                Component.translatable("screen.universal_config.quit"));
    }

    private boolean stackButtons(int buttonWidth) {
        return panelWidth() < buttonWidth * 2 + BUTTON_GAP + 24;
    }

    private static String titleKey(State state) {
        return switch (state) {
            case SCHEDULED -> "screen.universal_config.apply_scheduled_title";
            case RESTART_FAILED -> "screen.universal_config.restart_failed_title";
            case PENDING_INVALID -> "screen.universal_config.pending_invalid_title";
        };
    }
}
