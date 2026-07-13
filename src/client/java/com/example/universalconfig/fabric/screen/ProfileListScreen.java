package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.ProfileSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class ProfileListScreen extends Screen {
    private static final int EDGE = 12;
    private static final int GAP = 12;
    private static final int HEADER_HEIGHT = 52;
    private static final int PANEL_TOP = 56;
    private static final int FOOTER_HEIGHT = 36;
    private static final int PANEL_BOTTOM_GAP = 8;
    private static final int PANEL_HEADER_HEIGHT = 22;
    private static final int PANEL_PADDING = 12;
    private static final int ACTION_PANEL_HEIGHT = 98;
    private static final int ACTION_HEADER_HEIGHT = 18;
    private static final int SECTION_GAP = 8;
    private static final int CARD_STEP = 72;
    private static final int CARD_HEIGHT = 64;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int ACTION_BUTTON_ROWS = 3;
    private static final int ACTION_BUTTON_GROUP_HEIGHT = ACTION_BUTTON_ROWS * BUTTON_HEIGHT
            + (ACTION_BUTTON_ROWS - 1) * BUTTON_GAP;

    private static final int PANEL_COLOR = 0xB0101010;
    private static final int PANEL_HEADER_COLOR = 0x301F1F1F;
    private static final int CARD_COLOR = 0x50181818;
    private static final int SELECTED_CARD_COLOR = 0x60404040;
    private static final int DIVIDER_COLOR = 0x403F3F3F;
    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;
    private static final int SECONDARY_TEXT_COLOR = 0xFFBBBBBB;
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private List<ProfileSummary> profiles = new ArrayList<>();
    private PendingImport pendingImport;
    private Text status = Text.empty();
    private int selectedProfileIndex;
    private int listScroll;
    private int detailScroll;

    public ProfileListScreen(Screen parent) {
        super(Text.translatable("screen.universal_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
        rebuildButtons();
    }

    private void reload() {
        try {
            ProfileService service = ScreenUtil.service();
            profiles = service.listProfiles();
            pendingImport = service.readPendingImport(ScreenUtil.instancePath());
            selectedProfileIndex = profiles.isEmpty() ? -1 : Math.min(selectedProfileIndex, profiles.size() - 1);
            status = pendingImport == null
                    ? Text.translatable("screen.universal_config.common_folder", ".universal-config")
                    : Text.translatable("screen.universal_config.pending_import", pendingImport.profilePath);
        } catch (UniversalConfigException ex) {
            profiles = List.of();
            pendingImport = null;
            selectedProfileIndex = -1;
            status = ScreenUtil.errorText(ex);
        }
    }

    private int panelBottom() {
        return height - FOOTER_HEIGHT - PANEL_BOTTOM_GAP;
    }

    private int leftPanelWidth() {
        int available = width - EDGE * 2 - GAP;
        return Math.max(170, (int) (available * 0.38));
    }

    private int leftPanelRight() {
        return EDGE + leftPanelWidth();
    }

    private int rightPanelLeft() {
        return leftPanelRight() + GAP;
    }

    private int rightPanelWidth() {
        return width - EDGE - rightPanelLeft();
    }

    private int listTop() {
        return PANEL_TOP + PANEL_HEADER_HEIGHT + PANEL_PADDING;
    }

    private int listBottom() {
        return panelBottom() - PANEL_PADDING;
    }

    private int visibleRowCount() {
        return Math.max(1, (listBottom() - listTop() + BUTTON_GAP) / CARD_STEP);
    }

    private int firstVisibleRow() {
        return Math.min(listScroll, Math.max(0, profiles.size() - visibleRowCount()));
    }

    private int cardX() {
        return EDGE + PANEL_PADDING;
    }

    private int cardWidth() {
        return leftPanelWidth() - PANEL_PADDING * 2 - SCROLLBAR_WIDTH - SCROLLBAR_GAP;
    }

    private int cardY(int visibleRow) {
        return listTop() + visibleRow * CARD_STEP;
    }

    private int detailX() {
        return rightPanelLeft() + PANEL_PADDING;
    }

    private int detailWidth() {
        return rightPanelWidth() - PANEL_PADDING * 2;
    }

    private int actionsPanelY() {
        return panelBottom() - ACTION_PANEL_HEIGHT;
    }

    private int detailPanelBottom() {
        return actionsPanelY() - SECTION_GAP;
    }

    private int detailViewportTop() {
        return PANEL_TOP + PANEL_HEADER_HEIGHT + PANEL_PADDING;
    }

    private int detailViewportBottom() {
        return detailPanelBottom() - PANEL_PADDING;
    }

    private int detailContentY() {
        return detailContentBaseY() - detailScroll;
    }

    private int detailContentBaseY() {
        return detailViewportTop();
    }

    private int detailLineHeight() {
        return height < 400 ? 12 : 16;
    }

    private boolean showRestoreDescription() {
        return height >= 400;
    }

    private int detailFieldsEndY() {
        return detailContentBaseY() + detailLineHeight() * 4;
    }

    private int detailContentEndBaseY() {
        int y = detailFieldsEndY();
        if (showRestoreDescription()) {
            y += 12 + detailLineHeight();
        }
        return y + PANEL_PADDING;
    }

    private int actionsButtonStartY() {
        int contentTop = actionsPanelY() + ACTION_HEADER_HEIGHT;
        int contentHeight = ACTION_PANEL_HEIGHT - ACTION_HEADER_HEIGHT;
        int verticalInset = Math.max(0, (contentHeight - ACTION_BUTTON_GROUP_HEIGHT) / 2);
        return contentTop + verticalInset;
    }

    private int maxDetailScroll() {
        return Math.max(0, detailContentEndBaseY() - detailViewportBottom());
    }

    private int footerY() {
        return height - 28;
    }

    private int measuredButtonWidth(Text label, int minimum, int maximum) {
        int measured = textRenderer.getWidth(label) + 16;
        return Math.max(minimum, Math.min(maximum, measured));
    }

    private void rebuildButtons() {
        clearChildren();
        int firstRow = firstVisibleRow();
        int rowCount = Math.min(visibleRowCount(), profiles.size() - firstRow);
        detailScroll = Math.min(detailScroll, maxDetailScroll());
        for (int row = 0; row < rowCount; row++) {
            int index = firstRow + row;
            addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
                selectedProfileIndex = index;
                detailScroll = 0;
                rebuildButtons();
            }).dimensions(cardX(), cardY(row), cardWidth(), CARD_HEIGHT).build());
        }

        ProfileSummary selected = selectedProfile();
        if (selected != null) {
            Path path = selected.path();
            int x = detailX();
            int y = actionsButtonStartY();
            int buttonWidth = Math.max(70, (detailWidth() - BUTTON_GAP) / 2);
            int secondaryY = y + BUTTON_HEIGHT + BUTTON_GAP;
            int deleteY = secondaryY + BUTTON_HEIGHT + BUTTON_GAP;
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.view"), button -> openConfirm(path))
                    .dimensions(x, y, buttonWidth, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.restore"), button -> openConfirm(path))
                    .dimensions(x + buttonWidth + BUTTON_GAP, y, buttonWidth, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.duplicate"), button -> duplicate(path))
                    .dimensions(x, secondaryY, buttonWidth, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.export"), button -> export(path))
                    .dimensions(x + buttonWidth + BUTTON_GAP, secondaryY, buttonWidth, BUTTON_HEIGHT).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.delete"), button -> confirmDelete(path))
                    .dimensions(x, deleteY, detailWidth(), BUTTON_HEIGHT).build());
        }

        int right = rightPanelLeft() + rightPanelWidth() - PANEL_PADDING;
        Text saveLabel = Text.translatable("screen.universal_config.save_current");
        Text updateLabel = Text.translatable("screen.universal_config.refresh");
        Text closeLabel = Text.translatable("screen.universal_config.close");
        Text outputLabel = Text.translatable("screen.universal_config.open_folder");
        int saveWidth = measuredButtonWidth(saveLabel, 96, 144);
        int outputWidth = measuredButtonWidth(outputLabel, 72, 104);
        int closeWidth = measuredButtonWidth(closeLabel, 48, 72);
        int updateWidth = measuredButtonWidth(updateLabel, 52, 88);
        int outputX = right - outputWidth;
        int closeX = outputX - BUTTON_GAP - closeWidth;
        int updateX = closeX - BUTTON_GAP - updateWidth;
        addDrawableChild(ButtonWidget.builder(saveLabel, button -> client.setScreen(new ProfileCreateScreen(this)))
                .dimensions(EDGE, footerY(), saveWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(updateLabel, button -> {
            reload();
            rebuildButtons();
        }).dimensions(updateX, footerY(), updateWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(closeLabel, button -> client.setScreen(parent))
                .dimensions(closeX, footerY(), closeWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(outputLabel, button -> openOutputDirectory())
                .dimensions(outputX, footerY(), outputWidth, BUTTON_HEIGHT).build());
    }

    private ProfileSummary selectedProfile() {
        return selectedProfileIndex >= 0 && selectedProfileIndex < profiles.size()
                ? profiles.get(selectedProfileIndex)
                : null;
    }

    private void openConfirm(Path path) {
        client.setScreen(new ProfileConfirmScreen(this, path));
    }

    private void duplicate(Path path) {
        try {
            ScreenUtil.service().duplicateProfile(path);
            reload();
            rebuildButtons();
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void confirmDelete(Path path) {
        ProfileSummary summary = profiles.stream().filter(profile -> profile.path().equals(path)).findFirst().orElse(null);
        String name = summary == null ? translation("screen.universal_config.this_profile") : summary.manifest().name;
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                delete(path);
            } else {
                client.setScreen(this);
            }
        }, Text.translatable("screen.universal_config.delete_confirm", name),
                Text.translatable("screen.universal_config.delete_warning")));
    }

    private void delete(Path path) {
        try {
            ScreenUtil.service().deleteProfile(path);
            selectedProfileIndex = Math.max(0, selectedProfileIndex - 1);
            reload();
            rebuildButtons();
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void export(Path path) {
        try {
            Path exported = ScreenUtil.service().exportProfile(path, UniversalConfigPaths.exportDirectory(ScreenUtil.instancePath()));
            status = Text.translatable("screen.universal_config.exported", exported.getFileName());
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void openOutputDirectory() {
        try {
            Path directory = ScreenUtil.service().settings().rootDirectory().toAbsolutePath().normalize();
            Util.getOperatingSystem().open(directory.toUri());
            FileOperationLogger.info("OPEN_OUTPUT_DIRECTORY", directory, "opened by user");
            status = Text.translatable("screen.universal_config.folder_opened");
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= EDGE && mouseX <= leftPanelRight()
                && mouseY >= PANEL_TOP && mouseY <= panelBottom()) {
            int maxScroll = Math.max(0, profiles.size() - visibleRowCount());
            if (maxScroll <= 0) {
                listScroll = 0;
                return true;
            }
            int direction = amount > 0 ? -1 : amount < 0 ? 1 : 0;
            listScroll = Math.max(0, Math.min(maxScroll, listScroll + direction));
            rebuildButtons();
            return true;
        }
        if (mouseX >= rightPanelLeft() && mouseX <= width - EDGE
                && mouseY >= PANEL_TOP && mouseY <= detailPanelBottom()) {
            int maxScroll = maxDetailScroll();
            if (maxScroll <= 0) {
                detailScroll = 0;
                return true;
            }
            int direction = amount > 0 ? -1 : amount < 0 ? 1 : 0;
            detailScroll = Math.max(0, Math.min(maxScroll, detailScroll + direction));
            rebuildButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawHeader(context);
        drawPanels(context);
        drawListContent(context);
        drawDetailContent(context);
        super.render(context, mouseX, mouseY, delta);
        drawCardSurfaces(context);
        drawCardText(context);
        drawScrollbar(context);
        drawDetailScrollbar(context);
    }

    private void drawHeader(DrawContext context) {
        context.fill(0, 0, width, HEADER_HEIGHT, 0xB0181818);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 7, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.universal_config.subtitle"), width / 2, 21, 0xFFBBBBBB);
        drawTrimmed(context, status.getString(), EDGE, 36, width - EDGE * 2, MUTED_TEXT_COLOR);
    }

    private void drawPanels(DrawContext context) {
        drawPanel(context, EDGE, PANEL_TOP, leftPanelRight(), panelBottom());
        drawPanel(context, rightPanelLeft(), PANEL_TOP, width - EDGE, detailPanelBottom());
        drawPanel(context, rightPanelLeft(), actionsPanelY(), width - EDGE, panelBottom());
    }

    private void drawListContent(DrawContext context) {
        int left = EDGE;
        int right = leftPanelRight();
        drawPanelHeader(context, left, right, PANEL_TOP, PANEL_HEADER_HEIGHT, "screen.universal_config.profile_list",
                Text.translatable("screen.universal_config.profile_count", profiles.size()).getString());
        if (profiles.isEmpty()) {
            drawTrimmed(context, translation("screen.universal_config.empty_title"), left + PANEL_PADDING,
                    listTop() + 24, leftPanelWidth() - PANEL_PADDING * 2, 0xFFDDDDDD);
            drawTrimmed(context, translation("screen.universal_config.empty_hint"), left + PANEL_PADDING,
                    listTop() + 42, leftPanelWidth() - PANEL_PADDING * 2, MUTED_TEXT_COLOR);
        }
    }

    private void drawDetailContent(DrawContext context) {
        int left = rightPanelLeft();
        int right = width - EDGE;
        drawPanelHeader(context, left, right, PANEL_TOP, PANEL_HEADER_HEIGHT, "screen.universal_config.profile_detail", "");
        ProfileSummary selected = selectedProfile();
        if (selected == null) {
            drawTrimmed(context, translation("screen.universal_config.select_profile"), detailX(), detailViewportTop() + 12,
                    detailWidth(), MUTED_TEXT_COLOR);
        } else {
            context.enableScissor(left, detailViewportTop() - 2, right, detailViewportBottom());
            drawDetails(context, selected, detailX(), detailContentY(), detailWidth());
            context.disableScissor();
        }
        drawPanelHeader(context, left, right, actionsPanelY(), ACTION_HEADER_HEIGHT, "screen.universal_config.actions", "");
    }

    private void drawPanelHeader(DrawContext context, int left, int right, int top, int headerHeight, String headingKey, String trailing) {
        context.fill(left, top, right, top + headerHeight, PANEL_HEADER_COLOR);
        int headingY = top + 5;
        context.drawTextWithShadow(textRenderer, Text.translatable(headingKey), left + PANEL_PADDING, headingY, 0xFFFFFFFF);
        if (!trailing.isEmpty()) {
            int trailingWidth = Math.min(84, Math.max(32, right - left - 96));
            drawTrimmed(context, trailing, right - PANEL_PADDING - trailingWidth, headingY, trailingWidth, MUTED_TEXT_COLOR);
        }
        context.fill(left, top + headerHeight - 1, right, top + headerHeight, DIVIDER_COLOR);
    }

    private void drawCardSurfaces(DrawContext context) {
        int firstRow = firstVisibleRow();
        int rowCount = Math.min(visibleRowCount(), profiles.size() - firstRow);
        for (int row = 0; row < rowCount; row++) {
            int index = firstRow + row;
            int y = cardY(row);
            context.fill(cardX(), y, cardX() + cardWidth(), y + CARD_HEIGHT,
                    index == selectedProfileIndex ? SELECTED_CARD_COLOR : CARD_COLOR);
        }
    }

    private void drawCardText(DrawContext context) {
        int firstRow = firstVisibleRow();
        int rowCount = Math.min(visibleRowCount(), profiles.size() - firstRow);
        for (int row = 0; row < rowCount; row++) {
            ProfileManifest manifest = profiles.get(firstRow + row).manifest();
            int y = cardY(row);
            int textX = cardX() + 8;
            int textWidth = cardWidth() - 16;
            drawTrimmed(context, manifest.name, textX, y + 5, textWidth, 0xFFFFFFFF);
            String[] environmentLines = environmentLines(manifest);
            drawTrimmed(context, environmentLines[0], textX, y + 20, textWidth, SECONDARY_TEXT_COLOR);
            drawTrimmed(context, environmentLines[1], textX, y + 35, textWidth, SECONDARY_TEXT_COLOR);
            drawTrimmed(context, translation("screen.universal_config.updated") + ": " + formatDate(manifest.updatedAt),
                    textX, y + 50, textWidth, MUTED_TEXT_COLOR);
        }
    }

    private void drawScrollbar(DrawContext context) {
        int maxScroll = profiles.size() - visibleRowCount();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = listTop();
        int trackBottom = listBottom();
        int trackHeight = trackBottom - trackTop;
        int thumbHeight = Math.max(14, trackHeight * visibleRowCount() / profiles.size());
        int thumbTravel = trackHeight - thumbHeight;
        int thumbTop = trackTop + thumbTravel * firstVisibleRow() / maxScroll;
        int scrollbarX = leftPanelRight() - PANEL_PADDING - SCROLLBAR_WIDTH;
        context.fill(scrollbarX, trackTop, scrollbarX + SCROLLBAR_WIDTH, trackBottom, 0x403F3F3F);
        context.fill(scrollbarX, thumbTop, scrollbarX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFF8A8A8A);
    }

    private void drawDetailScrollbar(DrawContext context) {
        int maxScroll = maxDetailScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = detailViewportTop();
        int trackBottom = detailViewportBottom();
        int trackHeight = trackBottom - trackTop;
        int contentHeight = detailContentEndBaseY() - detailContentBaseY();
        int thumbHeight = Math.max(14, trackHeight * trackHeight / Math.max(trackHeight, contentHeight));
        int thumbTravel = trackHeight - thumbHeight;
        int thumbTop = trackTop + thumbTravel * detailScroll / maxScroll;
        int scrollbarX = rightPanelLeft() + rightPanelWidth() - PANEL_PADDING - SCROLLBAR_WIDTH;
        context.fill(scrollbarX, trackTop, scrollbarX + SCROLLBAR_WIDTH, trackBottom, 0x403F3F3F);
        context.fill(scrollbarX, thumbTop, scrollbarX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFF8A8A8A);
    }

    private void drawDetails(DrawContext context, ProfileSummary summary, int x, int y, int maxWidth) {
        ProfileManifest manifest = summary.manifest();
        int lineHeight = detailLineHeight();
        drawField(context, "screen.universal_config.name", manifest.name, x, y, maxWidth);
        drawField(context, "screen.universal_config.environment", environmentText(manifest), x, y + lineHeight, maxWidth);
        drawField(context, "screen.universal_config.updated", formatDate(manifest.updatedAt), x, y + lineHeight * 2, maxWidth);
        drawField(context, "screen.universal_config.targets", includesText(manifest), x, y + lineHeight * 3, maxWidth);
        int descriptionY = y + lineHeight * 4 + 12;
        if (showRestoreDescription()) {
            drawTrimmed(context, translation("screen.universal_config.restore_description"), x, descriptionY, maxWidth, MUTED_TEXT_COLOR);
        }
    }

    private void drawField(DrawContext context, String labelKey, String value, int x, int y, int maxWidth) {
        drawTrimmed(context, Text.translatable(labelKey).getString() + ": " + value, x, y, maxWidth, 0xFFFFFFFF);
    }

    private String environmentText(ProfileManifest manifest) {
        if (manifest.source == null) {
            return translation("screen.universal_config.environment_unknown");
        }
        String[] lines = environmentLines(manifest);
        return lines[0] + " / " + lines[1];
    }

    private String[] environmentLines(ProfileManifest manifest) {
        if (manifest.source == null) {
            return new String[]{translation("screen.universal_config.environment_unknown"), ""};
        }
        return new String[]{"Minecraft " + safe(manifest.source.minecraftVersion), formatLoader(manifest.source.loader)};
    }

    private String formatLoader(String value) {
        String loader = safe(value);
        return switch (loader.toLowerCase()) {
            case "fabric" -> "Fabric";
            case "forge" -> "Forge";
            case "neoforge" -> "NeoForge";
            default -> loader.substring(0, 1).toUpperCase() + loader.substring(1);
        };
    }

    private String includesText(ProfileManifest manifest) {
        if (manifest.includes == null) {
            return translation("screen.universal_config.targets_unknown");
        }
        List<String> includes = new ArrayList<>();
        if (manifest.includes.keybinds) {
            includes.add(translation("screen.universal_config.target_keybinds"));
        }
        if (manifest.includes.clientOptions) {
            includes.add(translation("screen.universal_config.target_client"));
        }
        if (manifest.includes.modConfigs) {
            includes.add(translation("screen.universal_config.target_mods"));
        }
        return includes.isEmpty() ? "なし" : String.join("・", includes);
    }

    private String formatDate(String value) {
        if (value == null || value.isBlank()) {
            return "日時不明";
        }
        try {
            return DISPLAY_DATE.format(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return DISPLAY_DATE.format(OffsetDateTime.parse(value));
            } catch (DateTimeParseException ignoredAgain) {
                return value.length() > 16 ? value.substring(0, 16) : value;
            }
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? translation("screen.universal_config.unknown") : value;
    }

    private String translation(String key) {
        return Text.translatable(key).getString();
    }

    private void drawPanel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, PANEL_COLOR);
    }

    private void drawTrimmed(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text == null ? "" : text, Math.max(0, maxWidth)), x, y, color);
    }
}
