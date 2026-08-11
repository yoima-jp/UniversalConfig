package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.ProfileDiff;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.RiskLevel;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileConfirmScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 520;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_TOP = 34;
    private static final int PANEL_BOTTOM_MARGIN = 40;
    private static final int PANEL_PADDING = 14;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;
    private static final int LINE_HEIGHT = 14;
    private static final int PANEL_COLOR = 0xE0181818;
    private static final int PANEL_BORDER_LIGHT = 0xFF6E6E6E;
    private static final int PANEL_BORDER_DARK = 0xFF101010;
    private static final int INNER_COLOR = 0xB00B0B0B;
    private static final int MUTED_TEXT_COLOR = 0xFFB8B8B8;
    private static final int WARNING_COLOR = 0xFFFF7777;
    private static final int CHECK_COLOR = 0xFF55FF55;

    private final Screen parent;
    private final Path profilePath;
    private ProfileManifest manifest;
    private ProfileDiff diff = new ProfileDiff();
    private List<DisplayLine> detailLines = List.of();
    private Component status = Component.empty();
    private boolean detailsVisible;
    private int scroll;

    public ProfileConfirmScreen(Screen parent, Path profilePath) {
        super(Component.translatable("screen.universal_config.profile_confirm_title"));
        this.parent = parent;
        this.profilePath = profilePath;
    }

    @Override
    protected void init() {
        loadDiff();
        rebuildButtons();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void loadDiff() {
        try {
            ProfileService service = ScreenUtil.service();
            manifest = service.readManifest(profilePath);
            diff = service.diff(ScreenUtil.instancePath(), profilePath, ScreenUtil.environment());
            detailLines = buildDetailLines();
            scroll = Math.min(scroll, maxScroll());
        } catch (UniversalConfigException | RuntimeException ex) {
            manifest = null;
            diff = new ProfileDiff();
            detailLines = List.of();
            status = ex instanceof UniversalConfigException universalConfigException
                    ? ScreenUtil.errorText(universalConfigException)
                    : Component.translatable("screen.universal_config.confirm_load_failed");
            FileOperationLogger.failure("LOAD_PROFILE_CONFIRM", profilePath, "failed", ex);
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        int totalWidth = Math.min(panelWidth() - PANEL_PADDING * 2, 360);
        int primaryWidth = Math.max(120, totalWidth - 86);
        int left = width / 2 - totalWidth / 2;
        int buttonY = height - 30;
        Button scheduleButton = Button.builder(scheduleButtonLabel(), button -> schedule())
                .bounds(left, buttonY, primaryWidth, BUTTON_HEIGHT).build();
        scheduleButton.active = manifest != null;
        addRenderableWidget(scheduleButton);
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.back"), button -> onClose())
                .bounds(left + primaryWidth + BUTTON_GAP, buttonY, totalWidth - primaryWidth - BUTTON_GAP, BUTTON_HEIGHT).build());

        if (manifest != null && !detailLines.isEmpty()) {
            Component label = Component.translatable(detailsVisible
                    ? "screen.universal_config.hide_details"
                    : "screen.universal_config.show_details");
            int detailsWidth = Math.min(112, Math.max(74, font.width(label) + 16));
            addRenderableWidget(Button.builder(label, button -> {
                detailsVisible = !detailsVisible;
                scroll = 0;
                rebuildButtons();
            }).bounds(panelRight() - PANEL_PADDING - detailsWidth, panelBottom() - 28, detailsWidth, BUTTON_HEIGHT).build());
        }
    }

    private List<DisplayLine> buildDetailLines() {
        List<DisplayLine> result = new ArrayList<>();
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_source",
                safe(manifest.source == null ? null : manifest.source.minecraftVersion),
                safe(manifest.source == null ? null : manifest.source.loader),
                safe(manifest.source == null ? null : manifest.source.loaderVersion)), false));
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_current",
                ScreenUtil.environment().minecraftVersion(), ScreenUtil.environment().loaderId(),
                ScreenUtil.environment().loaderVersion()), false));
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_compatibility",
                safe(manifest.compatibility == null ? null : manifest.compatibility.minecraftVersionRange),
                safe(manifest.compatibility == null ? null : manifest.compatibility.mode)), false));
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_tested",
                testedVersions()), false));
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_apply_mode"), false));
        result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_risk",
                Component.translatable(riskLevelKey(diff.riskLevel))), diff.riskLevel == RiskLevel.HIGH));
        appendSection(result, "screen.universal_config.confirm_warnings", diff.warnings, true);
        appendSection(result, "screen.universal_config.confirm_checksums", diff.checksumWarnings, true);
        appendSection(result, "screen.universal_config.confirm_changed_keybinds", diff.changedKeybinds, false);
        appendSection(result, "screen.universal_config.confirm_added_files", diff.addedFiles, false);
        appendSection(result, "screen.universal_config.confirm_replaced_files", diff.replacedFiles, false);
        appendSection(result, "screen.universal_config.confirm_skipped", diff.skippedItems, false);
        return result;
    }

    private String testedVersions() {
        if (manifest.compatibility == null || manifest.compatibility.testedVersions == null
                || manifest.compatibility.testedVersions.isEmpty()) {
            return translation("screen.universal_config.unknown");
        }
        return String.join(", ", manifest.compatibility.testedVersions);
    }

    private void appendSection(List<DisplayLine> result, String titleKey, List<String> values, boolean warning) {
        if (values == null || values.isEmpty()) {
            return;
        }
        result.add(new DisplayLine(Component.translatable(titleKey), warning));
        for (String value : values) {
            result.add(new DisplayLine(Component.translatable("screen.universal_config.confirm_list_item", value), warning));
        }
    }

    private Component scheduleButtonLabel() {
        return switch (diff.riskLevel) {
            case HIGH -> Component.translatable("screen.universal_config.confirm_schedule_high");
            case MEDIUM -> Component.translatable("screen.universal_config.use_profile");
            case LOW -> Component.translatable("screen.universal_config.use_profile");
        };
    }

    private String riskLevelKey(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> "screen.universal_config.risk_high";
            case MEDIUM -> "screen.universal_config.risk_medium";
            case LOW -> "screen.universal_config.risk_low";
        };
    }

    private void schedule() {
        if (manifest == null) {
            status = Component.translatable("screen.universal_config.confirm_load_failed");
            return;
        }
        try {
            ScreenUtil.service().scheduleApplyOnNextStart(ScreenUtil.instancePath(), profilePath, ScreenUtil.environment());
            minecraft.setScreen(new ApplyScheduledScreen(parent));
        } catch (UniversalConfigException | RuntimeException ex) {
            status = ex instanceof UniversalConfigException universalConfigException
                    ? ScreenUtil.errorText(universalConfigException)
                    : Component.translatable("screen.universal_config.schedule_failed");
            FileOperationLogger.failure("SCHEDULE_PROFILE_APPLY", profilePath, "failed", ex);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!detailsVisible) {
            return super.mouseScrolled(mouseX, mouseY, amount);
        }
        int direction = amount > 0 ? -2 : amount < 0 ? 2 : 0;
        scroll = Math.max(0, Math.min(maxScroll(), scroll + direction));
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
        drawPanel(context);
        super.render(context, mouseX, mouseY, delta);
        if (!status.getString().isEmpty()) {
            drawTrimmed(context, status.getString(), panelLeft() + PANEL_PADDING, panelTop() + 4,
                    panelWidth() - PANEL_PADDING * 2, WARNING_COLOR);
        }
    }

    private void drawPanel(GuiGraphics context) {
        drawBorderedRect(context, panelLeft(), panelTop(), panelRight(), panelBottom(), PANEL_COLOR);
        if (manifest == null) {
            drawTrimmed(context, status.getString(), panelLeft() + PANEL_PADDING, panelTop() + 18,
                    panelWidth() - PANEL_PADDING * 2, WARNING_COLOR);
            return;
        }
        int x = panelLeft() + PANEL_PADDING;
        int contentRight = panelRight() - PANEL_PADDING;
        int y = panelTop() + 16;
        context.drawString(font, Component.literal(safe(manifest.name)), x, y, 0xFFFFFFFF);
        y += 18;
        String minecraftVersion = manifest.source == null ? translation("screen.universal_config.unknown")
                : safe(manifest.source.minecraftVersion);
        String loader = manifest.source == null ? translation("screen.universal_config.unknown")
                : safe(manifest.source.loader);
        drawTrimmed(context, Component.translatable("screen.universal_config.confirm_environment", minecraftVersion, loader).getString(),
                x, y, contentRight - x, MUTED_TEXT_COLOR);
        y += 22;
        if (detailsVisible) {
            drawDetails(context, x, y, contentRight);
            return;
        }
        context.drawString(font, Component.translatable("screen.universal_config.included_settings"), x, y, 0xFFFFFFFF);
        y += 16;
        if (manifest.includes != null) {
            y = drawInclude(context, manifest.includes.keybinds, "screen.universal_config.target_keybinds", x, y);
            y = drawInclude(context, manifest.includes.clientOptions, "screen.universal_config.target_client", x, y);
            y = drawInclude(context, manifest.includes.modConfigs, "screen.universal_config.target_mods", x, y);
        }
        y += 7;
        context.fill(x, y, contentRight, y + 1, 0xFF494949);
        y += 10;
        drawTrimmed(context, translation("screen.universal_config.confirm_backup_short"), x, y,
                contentRight - x, MUTED_TEXT_COLOR);
        y += LINE_HEIGHT;
        drawTrimmed(context, translation("screen.universal_config.confirm_restart_short"), x, y,
                contentRight - x, MUTED_TEXT_COLOR);

    }

    private void drawDetails(GuiGraphics context, int x, int detailsTop, int contentRight) {
        int detailsBottom = panelBottom() - 34;
        context.fill(x, detailsTop, contentRight, detailsBottom, INNER_COLOR);
        context.enableScissor(x, detailsTop, contentRight, detailsBottom);
        int lineY = detailsTop + 7;
        int visibleLines = Math.max(1, (detailsBottom - detailsTop - 12) / LINE_HEIGHT);
        for (int index = scroll; index < Math.min(detailLines.size(), scroll + visibleLines); index++) {
            DisplayLine line = detailLines.get(index);
            drawTrimmed(context, line.text().getString(), x + 7, lineY, contentRight - x - 14,
                    line.warning() ? WARNING_COLOR : MUTED_TEXT_COLOR);
            lineY += LINE_HEIGHT;
        }
        context.disableScissor();
    }

    private int drawInclude(GuiGraphics context, boolean included, String key, int x, int y) {
        if (included) {
            context.drawString(font, Component.literal("✓"), x, y, CHECK_COLOR);
            context.drawString(font, Component.translatable(key), x + 14, y, 0xFFFFFFFF);
            return y + LINE_HEIGHT;
        }
        return y;
    }

    private int panelWidth() {
        return Math.min(MAX_PANEL_WIDTH, Math.max(1, width - PANEL_MARGIN * 2));
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelRight() {
        return panelLeft() + panelWidth();
    }

    private int panelTop() {
        return Math.min(PANEL_TOP, Math.max(8, height / 8));
    }

    private int panelBottom() {
        return Math.max(panelTop() + 100, height - PANEL_BOTTOM_MARGIN);
    }

    private int maxScroll() {
        if (!detailsVisible) {
            return 0;
        }
        int detailsTop = panelTop() + 56;
        int detailsBottom = panelBottom() - 34;
        int visibleLines = Math.max(1, (detailsBottom - detailsTop - 12) / LINE_HEIGHT);
        return Math.max(0, detailLines.size() - visibleLines);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? translation("screen.universal_config.unknown") : value;
    }

    private String translation(String key) {
        return Component.translatable(key).getString();
    }

    private void drawBorderedRect(GuiGraphics context, int left, int top, int right, int bottom, int fill) {
        context.fill(left, top, right, bottom, PANEL_BORDER_DARK);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, PANEL_BORDER_LIGHT);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, fill);
    }

    private void drawTrimmed(GuiGraphics context, String text, int x, int y, int maxWidth, int color) {
        context.drawString(font,
                font.plainSubstrByWidth(text == null ? "" : text, Math.max(0, maxWidth)), x, y, color);
    }

    private record DisplayLine(Component text, boolean warning) {
    }
}
