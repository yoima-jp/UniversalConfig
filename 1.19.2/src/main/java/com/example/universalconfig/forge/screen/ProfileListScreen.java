package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.ProfileSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.forge.ForgeRestartService;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;


import net.minecraft.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProfileListScreen extends Screen {
    private static final int MIN_EDGE = 10;
    private static final int MAX_CONTENT_WIDTH = 760;
    private static final int COLUMN_GAP = 8;
    private static final int HEADER_HEIGHT = 46;
    private static final int PENDING_HEIGHT = 30;
    private static final int PANEL_GAP = 8;
    private static final int PANEL_HEADER_HEIGHT = 24;
    private static final int PANEL_PADDING = 10;
    private static final int FOOTER_HEIGHT = 30;
    private static final int BUTTON_HEIGHT = 20;
    private static final int PRIMARY_BUTTON_HEIGHT = 24;
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_GAP = 3;
    private static final int CARD_STEP = CARD_HEIGHT + CARD_GAP;
    private static final int LIST_SCROLL_STEP = 18;
    private static final int DETAIL_SCROLL_STEP = 18;
    private static final int DETAIL_LINE_HEIGHT = 12;
    private static final int PROFILE_ICON_SIZE = 28;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int REORDER_BUTTON_WIDTH = 16;
    private static final int REORDER_BUTTON_HEIGHT = 16;
    private static final int MORE_BUTTON_WIDTH = 34;
    private static final int MENU_WIDTH = 132;
    private static final int MENU_ITEM_GAP = 2;
    private static final int MENU_ITEM_COUNT = 6;
    private static final int MENU_HEIGHT = MENU_ITEM_COUNT * BUTTON_HEIGHT + (MENU_ITEM_COUNT - 1) * MENU_ITEM_GAP + 8;
    private static final int CLOSE_BUTTON_SIZE = 20;
    private static final String SAFE_DATE_PATTERN = "yyyy/MM/dd HH:mm";

    private static final int PANEL_COLOR = 0xD0181818;
    private static final int MENU_COLOR = 0xFF181818;
    private static final int PANEL_INNER_COLOR = 0xB00B0B0B;
    private static final int PANEL_HEADER_COLOR = 0xD02C2C2C;
    private static final int PANEL_BORDER_DARK = 0xFF101010;
    private static final int PANEL_BORDER_LIGHT = 0xFF6E6E6E;
    private static final int CARD_COLOR = 0xFF303030;
    private static final int CARD_HOVER_COLOR = 0xFF3A3A3A;
    private static final int SELECTED_CARD_COLOR = 0xFF465064;
    private static final int SELECTED_BORDER_COLOR = 0xFFD8E3FF;
    private static final int DIVIDER_COLOR = 0xFF494949;
    private static final int MUTED_TEXT_COLOR = 0xFFAAAAAA;
    private static final int SECONDARY_TEXT_COLOR = 0xFFD0D0D0;
    private static final int PENDING_COLOR = 0xE03B321B;
    private static final int PENDING_BORDER_COLOR = 0xFF8A7130;
    private static final int WARNING_COLOR = 0xFFFFD34E;
    private static final int CHECK_COLOR = 0xFF55FF55;
    private final Screen parent;
    private List<ProfileSummary> profiles = new ArrayList<>();
    private PendingImport pendingImport;
    private Path defaultProfilePath;
    private Component status = Component.empty();
    private final List<Button> moreMenuButtons = new ArrayList<>();
    private final List<Button> reorderButtons = new ArrayList<>();
    private int selectedProfileIndex;
    private int listScroll;
    private int detailScroll;
    private boolean moreMenuOpen;
    private boolean restarting;
    private boolean cancelingPendingApply;
    private int draggingProfileIndex = -1;
    private int dragTargetIndex = -1;
    private double dragStartY;
    private boolean draggingProfile;
    private String dateFormatterLanguage;
    private DateTimeFormatter dateFormatter;

    public ProfileListScreen(Screen parent) {
        super(Component.translatable("screen.universal_config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
        rebuildButtons();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void reload() {
        Path selectedPath = selectedProfile() == null ? null : selectedProfile().path();
        try {
            ProfileService service = ScreenUtil.service();
            profiles = service.listProfiles();
            pendingImport = service.readPendingImport(ScreenUtil.instancePath());
            defaultProfilePath = service.resolveDefaultProfile(ScreenUtil.instancePath());
            selectedProfileIndex = indexOfPath(selectedPath);
            if (selectedProfileIndex < 0) {
                selectedProfileIndex = profiles.isEmpty() ? -1 : 0;
            }
            listScroll = Math.min(listScroll, maxListScroll());
            detailScroll = Math.min(detailScroll, maxDetailScroll());
        } catch (UniversalConfigException | RuntimeException ex) {
            profiles = java.util.Collections.emptyList();
            pendingImport = null;
            defaultProfilePath = null;
            selectedProfileIndex = -1;
            listScroll = 0;
            detailScroll = 0;
            status = ex instanceof UniversalConfigException
                    ? ScreenUtil.errorText((UniversalConfigException) ex)
                    : Component.translatable("screen.universal_config.load_failed");
        }
    }

    private int indexOfPath(Path path) {
        if (path == null) {
            return -1;
        }
        for (int index = 0; index < profiles.size(); index++) {
            if (profiles.get(index).path().equals(path)) {
                return index;
            }
        }
        return -1;
    }

    private int contentWidth() {
        return Math.min(MAX_CONTENT_WIDTH, Math.max(1, width - MIN_EDGE * 2));
    }

    private int contentLeft() {
        return (width - contentWidth()) / 2;
    }

    private int mainTop() {
        return HEADER_HEIGHT + (pendingImport == null ? 0 : PENDING_HEIGHT + PANEL_GAP);
    }

    private int mainBottom() {
        return height - FOOTER_HEIGHT - PANEL_GAP;
    }

    private int leftPanelWidth() {
        int available = contentWidth() - COLUMN_GAP;
        return Math.max(142, available * 49 / 100);
    }

    private int leftPanelRight() {
        return contentLeft() + leftPanelWidth();
    }

    private int rightPanelLeft() {
        return leftPanelRight() + COLUMN_GAP;
    }

    private int rightPanelRight() {
        return contentLeft() + contentWidth();
    }

    private int listTop() {
        return mainTop() + PANEL_HEADER_HEIGHT + PANEL_PADDING;
    }

    private int listBottom() {
        return mainBottom() - PANEL_PADDING;
    }

    private int listViewportHeight() {
        return Math.max(0, listBottom() - listTop());
    }

    private int maxListScroll() {
        int contentHeight = profiles.isEmpty() ? 0 : profiles.size() * CARD_STEP - CARD_GAP;
        return Math.max(0, contentHeight - listViewportHeight());
    }

    private int firstVisibleRow() {
        return Math.min(profiles.size(), listScroll / CARD_STEP);
    }

    private int lastVisibleRowExclusive() {
        return Math.min(profiles.size(), (listScroll + listViewportHeight() + CARD_STEP - 1) / CARD_STEP);
    }

    private int cardX() {
        return contentLeft() + PANEL_PADDING;
    }

    private int cardWidth() {
        return leftPanelWidth() - PANEL_PADDING * 2 - SCROLLBAR_WIDTH - 5;
    }

    private int cardY(int profileIndex) {
        return listTop() + profileIndex * CARD_STEP - listScroll;
    }

    private int detailX() {
        return rightPanelLeft() + PANEL_PADDING;
    }

    private int detailWidth() {
        return rightPanelRight() - rightPanelLeft() - PANEL_PADDING * 2;
    }

    private int primaryButtonY() {
        return mainBottom() - PANEL_PADDING - PRIMARY_BUTTON_HEIGHT;
    }

    private int detailViewportTop() {
        return mainTop() + PANEL_HEADER_HEIGHT + 7;
    }

    private int detailViewportBottom() {
        return primaryButtonY() - 7;
    }

    private int detailContentWidth() {
        return Math.max(40, detailWidth() - 26);
    }

    private int detailViewportHeight() {
        return Math.max(0, detailViewportBottom() - detailViewportTop());
    }

    private int maxDetailScroll() {
        ProfileSummary selected = selectedProfile();
        return selected == null ? 0 : Math.max(0,
                detailContentHeight(selected.manifest()) - detailViewportHeight());
    }

    private int footerButtonY() {
        return height - FOOTER_HEIGHT + 2;
    }

    private int pendingTop() {
        return HEADER_HEIGHT;
    }

    private int pendingButtonWidth(Component label) {
        return Math.max(88, Math.min(128, font.width(label) + 20));
    }

    private int menuX() {
        return rightPanelRight() - PANEL_PADDING - MENU_WIDTH;
    }

    private int menuY() {
        return primaryButtonY() - MENU_HEIGHT - 4;
    }

    private void rebuildButtons() {
        clearWidgets();
        moreMenuButtons.clear();
        reorderButtons.clear();
        int firstRow = firstVisibleRow();
        int lastRow = lastVisibleRowExclusive();
        for (int index = firstRow; index < lastRow; index++) {
            int profileIndex = index;
            ProfileSummary profile = profiles.get(profileIndex);
            MutableComponent narration = Component.literal(profileName(profile.manifest()));
            int fullCardY = cardY(index);
            int clippedTop = Math.max(listTop(), fullCardY);
            int clippedBottom = Math.min(listBottom(), fullCardY + CARD_HEIGHT);
            if (clippedBottom <= clippedTop) {
                continue;
            }
            Button profileButton = new ProfileCardButton(cardX(), clippedTop, cardWidth(),
                    clippedBottom - clippedTop, narration, button -> selectProfile(profileIndex));
            // The empty visual label avoids duplicating the custom two-line card. Narration still announces the
            // profile name, so keyboard and screen-reader users receive the same selection context.
            addRenderableWidget(profileButton);
            if (fullCardY >= listTop() && fullCardY + CARD_HEIGHT <= listBottom()) {
                addReorderButtons(profileIndex, fullCardY);
            }
        }

        ProfileSummary selected = selectedProfile();
        if (selected != null) {
            Path path = selected.path();
            int actionWidth = detailWidth() - MORE_BUTTON_WIDTH - 6;
            addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.use_profile"), button -> openConfirm(path))
                    .bounds(detailX(), primaryButtonY(), actionWidth, PRIMARY_BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.more"), button -> {
                moreMenuOpen = !moreMenuOpen;
                rebuildButtons();
            }).bounds(detailX() + actionWidth + 6, primaryButtonY(), MORE_BUTTON_WIDTH, PRIMARY_BUTTON_HEIGHT).build());
            if (moreMenuOpen) {
                addMoreMenuButtons(path);
            }
        } else {
            moreMenuOpen = false;
        }

        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.save_current"),
                        button -> minecraft.setScreen(new ProfileCreateScreen(this)))
                .bounds(contentLeft(), footerButtonY(), contentWidth(), BUTTON_HEIGHT).build());

        if (pendingImport != null) {
            Component restartLabel = Component.translatable("screen.universal_config.restart_now");
            Component cancelLabel = Component.translatable("screen.universal_config.apply_scheduled_cancel");
            int restartWidth = pendingButtonWidth(restartLabel);
            int cancelWidth = pendingButtonWidth(cancelLabel);
            int right = rightPanelRight() - 6;
            addRenderableWidget(Button.builder(cancelLabel, button -> cancelPendingApply())
                    .bounds(right - cancelWidth, pendingTop() + 5, cancelWidth, BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(restartLabel, button -> restartForPendingApply())
                    .bounds(right - cancelWidth - 6 - restartWidth, pendingTop() + 5, restartWidth, BUTTON_HEIGHT).build());
        }

        MutableComponent closeNarration = Component.translatable("screen.universal_config.close");
        addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose())
                .createNarration(ignored -> closeNarration)
                .bounds(width - CLOSE_BUTTON_SIZE - 8, 8, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)
                .build());
    }

    private void addMoreMenuButtons(Path path) {
        int x = menuX() + 4;
        int y = menuY() + 4;
        int width = MENU_WIDTH - 8;
        Component defaultLabel = Component.translatable(isDefaultProfile(path)
                ? "screen.universal_config.clear_default"
                : "screen.universal_config.set_default");
        addMoreMenuButton(Button.builder(defaultLabel, button -> toggleDefault(path))
                .bounds(x, y, width, BUTTON_HEIGHT).build());
        addMoreMenuButton(Button.builder(Component.translatable("screen.universal_config.open_folder"), button -> openProfileDirectory())
                .bounds(x, y + BUTTON_HEIGHT + MENU_ITEM_GAP, width, BUTTON_HEIGHT).build());
        addMoreMenuButton(Button.builder(Component.translatable("screen.universal_config.rename"), button -> rename(path))
                .bounds(x, y + (BUTTON_HEIGHT + MENU_ITEM_GAP) * 2, width, BUTTON_HEIGHT).build());
        addMoreMenuButton(Button.builder(Component.translatable("screen.universal_config.duplicate"), button -> duplicate(path))
                .bounds(x, y + (BUTTON_HEIGHT + MENU_ITEM_GAP) * 3, width, BUTTON_HEIGHT).build());
        addMoreMenuButton(Button.builder(Component.translatable("screen.universal_config.backups"), button -> openBackups())
                .bounds(x, y + (BUTTON_HEIGHT + MENU_ITEM_GAP) * 4, width, BUTTON_HEIGHT).build());
        addMoreMenuButton(Button.builder(Component.translatable("screen.universal_config.delete"), button -> confirmDelete(path))
                .bounds(x, y + (BUTTON_HEIGHT + MENU_ITEM_GAP) * 5, width, BUTTON_HEIGHT).build());
    }

    private void openBackups() {
        minecraft.setScreen(new BackupListScreen(this));
    }

    private void addMoreMenuButton(Button button) {
        moreMenuButtons.add(button);
        addRenderableWidget(button);
    }

    private void addReorderButtons(int profileIndex, int cardY) {
        int x = cardX() + cardWidth() - REORDER_BUTTON_WIDTH - 4;
        int y = cardY + 3;
        Button upButton = Button.builder(Component.literal("\u2191"), button -> moveProfile(profileIndex, profileIndex - 1))
                .createNarration(ignored -> Component.translatable("screen.universal_config.move_up"))
                .bounds(x, y, REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT).build();
        upButton.active = profileIndex > 0;
        Button downButton = Button.builder(Component.literal("\u2193"), button -> moveProfile(profileIndex, profileIndex + 1))
                .createNarration(ignored -> Component.translatable("screen.universal_config.move_down"))
                .bounds(x, y + REORDER_BUTTON_HEIGHT + 1, REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT).build();
        downButton.active = profileIndex < profiles.size() - 1;
        reorderButtons.add(upButton);
        reorderButtons.add(downButton);
        addRenderableWidget(upButton);
        addRenderableWidget(downButton);
    }

    private ProfileSummary selectedProfile() {
        return selectedProfileIndex >= 0 && selectedProfileIndex < profiles.size()
                ? profiles.get(selectedProfileIndex)
                : null;
    }

    private void selectProfile(int profileIndex) {
        if (profileIndex < 0 || profileIndex >= profiles.size()) {
            return;
        }
        selectedProfileIndex = profileIndex;
        detailScroll = 0;
        moreMenuOpen = false;
        rebuildButtons();
    }

    private void rename(Path path) {
        ProfileSummary summary = null;
        for (ProfileSummary profile : profiles) {
            if (profile.path().equals(path)) {
                summary = profile;
                break;
            }
        }
        String currentName = summary == null ? translation("screen.universal_config.this_profile")
                : profileName(summary.manifest());
        moreMenuOpen = false;
        minecraft.setScreen(new ProfileRenameScreen(this, path, currentName, () -> {
            status = Component.empty();
            reload();
            rebuildButtons();
        }));
    }

    private void moveProfile(int sourceIndex, int targetIndex) {
        if (sourceIndex < 0 || sourceIndex >= profiles.size()
                || targetIndex < 0 || targetIndex >= profiles.size()
                || sourceIndex == targetIndex) {
            return;
        }
        Path path = profiles.get(sourceIndex).path();
        try {
            ScreenUtil.service().moveProfile(ScreenUtil.instancePath(), path, targetIndex);
            status = Component.empty();
            moreMenuOpen = false;
            reload();
            selectedProfileIndex = indexOfPath(path);
            rebuildButtons();
        } catch (UniversalConfigException | RuntimeException ex) {
            handleActionFailure(ex, "screen.universal_config.reorder_failed");
        }
    }

    private void openConfirm(Path path) {
        try {
            minecraft.setScreen(new ProfileConfirmScreen(this, path));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("OPEN_PROFILE_CONFIRM", path, "failed", ex);
            status = Component.translatable("screen.universal_config.open_confirm_failed");
        }
    }

    private void confirmImport(Path source) {
        try {
            ProfileManifest manifest = ScreenUtil.service().readManifest(source);
            String name = profileName(manifest);
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    importProfile(source);
                } else {
                    minecraft.setScreen(this);
                }
            }, Component.translatable("screen.universal_config.import_confirm", name),
                    Component.translatable("screen.universal_config.import_warning")));
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("OPEN_IMPORT_CONFIRM", source, "invalid dropped profile", ex);
            status = Component.translatable("screen.universal_config.import_failed");
            rebuildButtons();
        }
    }

    private void importProfile(Path source) {
        try {
            // The service validates the archive again after confirmation so a replaced file cannot bypass validation.
            Path imported = ScreenUtil.service().importProfile(source);
            status = Component.empty();
            moreMenuOpen = false;
            reload();
            selectedProfileIndex = indexOfPath(imported);
            detailScroll = 0;
            rebuildButtons();
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("IMPORT_PROFILE_FROM_DROP", source, "failed", ex);
            status = Component.translatable("screen.universal_config.import_failed");
        }
        minecraft.setScreen(this);
    }

    private void duplicate(Path path) {
        try {
            ScreenUtil.service().duplicateProfile(path);
            status = Component.empty();
            moreMenuOpen = false;
            reload();
            rebuildButtons();
        } catch (UniversalConfigException | RuntimeException ex) {
            handleActionFailure(ex, "screen.universal_config.duplicate_failed");
        }
    }

    private void confirmDelete(Path path) {
        ProfileSummary summary = profiles.stream().filter(profile -> profile.path().equals(path)).findFirst().orElse(null);
        String name = summary == null ? translation("screen.universal_config.this_profile") : profileName(summary.manifest());
        try {
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    delete(path);
                } else {
                    minecraft.setScreen(this);
                }
            }, Component.translatable("screen.universal_config.delete_confirm", name),
                    Component.translatable("screen.universal_config.delete_warning")));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("OPEN_DELETE_CONFIRM", path, "failed", ex);
            status = Component.translatable("screen.universal_config.delete_failed");
        }
    }

    private void delete(Path path) {
        try {
            ScreenUtil.service().deleteProfile(ScreenUtil.instancePath(), path);
            status = Component.empty();
            selectedProfileIndex = Math.max(0, selectedProfileIndex - 1);
            moreMenuOpen = false;
            reload();
            rebuildButtons();
            minecraft.setScreen(this);
        } catch (UniversalConfigException | RuntimeException ex) {
            handleActionFailure(ex, "screen.universal_config.delete_failed");
            minecraft.setScreen(this);
        }
    }

    private void toggleDefault(Path path) {
        try {
            ProfileService service = ScreenUtil.service();
            if (isDefaultProfile(path)) {
                service.clearDefaultProfile(ScreenUtil.instancePath());
            } else {
                service.setDefaultProfile(ScreenUtil.instancePath(), path);
            }
            status = Component.empty();
            moreMenuOpen = false;
            reload();
            rebuildButtons();
        } catch (UniversalConfigException | RuntimeException ex) {
            handleActionFailure(ex, "screen.universal_config.default_failed");
        }
    }

    private void openProfileDirectory() {
        try {
            ProfileService service = ScreenUtil.service();
            Path directory = UniversalConfigPaths.profilesDirectory(service.settings()).toAbsolutePath().normalize();
            Util.getPlatform().openUri(directory.toUri());
            FileOperationLogger.info("OPEN_PROFILES_DIRECTORY", directory, "opened by user");
            status = Component.empty();
            moreMenuOpen = false;
            rebuildButtons();
        } catch (UniversalConfigException | RuntimeException ex) {
            handleActionFailure(ex, "screen.universal_config.open_folder_failed");
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.size() != 1 || !isDroppedProfileFile(paths.get(0))) {
            status = Component.translatable("screen.universal_config.drop_one_profile");
            rebuildButtons();
            return;
        }
        confirmImport(paths.get(0).toAbsolutePath().normalize());
    }

    private boolean isDroppedProfileFile(Path path) {
        if (path == null || path.getFileName() == null || !Files.isRegularFile(path)) {
            return false;
        }
        return path.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(UniversalConfigFormat.PROFILE_FILE_EXTENSION);
    }

    private void restartForPendingApply() {
        if (restarting) {
            return;
        }
        // 終了処理が始まるまでボタンは操作可能なため、連打で複数の待機プロセスを作らない。
        // 成功時は画面が閉じるまで維持し、準備に失敗した場合だけ再試行を許可する。
        restarting = true;
        Path pendingPath = UniversalConfigPaths.pendingImportFile(ScreenUtil.instancePath());
        try {
            if (!Files.isRegularFile(pendingPath) || ScreenUtil.service().readPendingImport(ScreenUtil.instancePath()) == null) {
                throw new IllegalStateException("Pending import could not be verified");
            }
            // The helper waits for this JVM to finish saving options.txt before starting the replacement process.
            ForgeRestartService.scheduleRestartAfterCurrentProcessExit();
            FileOperationLogger.info("RESTART_FOR_PENDING_APPLY", pendingPath, "restart scheduled");
            minecraft.stop();
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("RESTART_FOR_PENDING_APPLY", pendingPath, "failed", ex);
            restarting = false;
            minecraft.setScreen(ApplyScheduledScreen.restartFailed(this));
        }
    }

    private void cancelPendingApply() {
        if (cancelingPendingApply) {
            return;
        }
        cancelingPendingApply = true;
        Path pendingPath = UniversalConfigPaths.pendingImportFile(ScreenUtil.instancePath());
        try {
            // The profile and current settings remain untouched; only the next-start reservation is removed.
            ScreenUtil.service().clearPendingImport(ScreenUtil.instancePath());
            pendingImport = null;
            status = Component.empty();
            rebuildButtons();
        } catch (UniversalConfigException ex) {
            status = Component.translatable("screen.universal_config.cancel_failed");
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("CANCEL_PENDING_IMPORT", pendingPath, "unexpected screen failure", ex);
            status = Component.translatable("screen.universal_config.cancel_failed");
        } finally {
            cancelingPendingApply = false;
        }
    }

    private void handleActionFailure(Exception ex, String fallbackKey) {
        if (ex instanceof UniversalConfigException) {
            status = ScreenUtil.errorText((UniversalConfigException) ex);
        } else {
            status = Component.translatable(fallbackKey);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= contentLeft() && mouseX <= leftPanelRight()
                && mouseY >= mainTop() && mouseY <= mainBottom()) {
            int previousScroll = listScroll;
            int delta = amount > 0 ? -LIST_SCROLL_STEP : amount < 0 ? LIST_SCROLL_STEP : 0;
            listScroll = Math.max(0, Math.min(maxListScroll(), listScroll + delta));
            if (listScroll != previousScroll) {
                rebuildButtons();
            }
            return true;
        }
        if (mouseX >= rightPanelLeft() && mouseX <= rightPanelRight()
                && mouseY >= detailViewportTop() && mouseY <= detailViewportBottom()) {
            int previousScroll = detailScroll;
            int delta = amount > 0 ? -DETAIL_SCROLL_STEP : amount < 0 ? DETAIL_SCROLL_STEP : 0;
            detailScroll = Math.max(0, Math.min(maxDetailScroll(), detailScroll + delta));
            return detailScroll != previousScroll || amount != 0;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (moreMenuOpen && !inside(mouseX, mouseY, menuX(), menuY(), MENU_WIDTH, MENU_HEIGHT)
                && !inside(mouseX, mouseY, detailX() + detailWidth() - MORE_BUTTON_WIDTH, primaryButtonY(),
                MORE_BUTTON_WIDTH, PRIMARY_BUTTON_HEIGHT)) {
            moreMenuOpen = false;
            rebuildButtons();
        }
        if (button == 0) {
            int profileIndex = profileIndexAt(mouseX, mouseY);
            int reorderDelta = reorderDeltaAt(mouseX, mouseY);
            if (profileIndex >= 0 && reorderDelta != 0) {
                moveProfile(profileIndex, profileIndex + reorderDelta);
                return true;
            }
            if (profileIndex >= 0 && !insideReorderControls(mouseX, mouseY)) {
                draggingProfileIndex = profileIndex;
                dragTargetIndex = profileIndex;
                dragStartY = mouseY;
                draggingProfile = false;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingProfileIndex >= 0) {
            if (!draggingProfile && Math.abs(mouseY - dragStartY) > 3) {
                draggingProfile = true;
            }
            if (draggingProfile) {
                dragTargetIndex = dropIndexAt(mouseY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingProfileIndex >= 0) {
            int sourceIndex = draggingProfileIndex;
            int targetIndex = dragTargetIndex;
            boolean wasDragging = draggingProfile;
            draggingProfileIndex = -1;
            dragTargetIndex = -1;
            draggingProfile = false;
            if (wasDragging) {
                moveProfile(sourceIndex, targetIndex);
            } else if (profileIndexAt(mouseX, mouseY) == sourceIndex) {
                selectProfile(sourceIndex);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int profileIndexAt(double mouseX, double mouseY) {
        if (mouseX < cardX() || mouseX >= cardX() + cardWidth()
                || mouseY < listTop() || mouseY >= listBottom()) {
            return -1;
        }
        int relativeY = (int) mouseY - listTop() + listScroll;
        int index = relativeY / CARD_STEP;
        int rowOffset = relativeY % CARD_STEP;
        return index >= 0 && index < profiles.size() && rowOffset < CARD_HEIGHT ? index : -1;
    }

    private boolean insideReorderControls(double mouseX, double mouseY) {
        return reorderDeltaAt(mouseX, mouseY) != 0;
    }

    private int reorderDeltaAt(double mouseX, double mouseY) {
        int index = profileIndexAt(mouseX, mouseY);
        if (index < 0) {
            return 0;
        }
        int x = cardX() + cardWidth() - REORDER_BUTTON_WIDTH - 4;
        int y = cardY(index) + 3;
        if (inside(mouseX, mouseY, x, y, REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT)) {
            return -1;
        }
        return inside(mouseX, mouseY, x, y + REORDER_BUTTON_HEIGHT + 1,
                REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT) ? 1 : 0;
    }

    private int dropIndexAt(double mouseY) {
        if (profiles.isEmpty()) {
            return -1;
        }
        int relativeY = (int) mouseY - listTop() + listScroll - CARD_HEIGHT / 2;
        int index = relativeY / CARD_STEP;
        return Math.max(0, Math.min(profiles.size() - 1, index));
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public void render(PoseStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawHeader(context);
        drawPanels(context);
        drawListContent(context);
        drawDetailContent(context);
        super.render(context, mouseX, mouseY, delta);
        drawProfileCards(context, mouseX, mouseY);
        drawDragIndicator(context);
        drawReorderButtons(context, mouseX, mouseY, delta);
        drawScrollbar(context);
        drawDetailScrollbar(context);
        drawStatus(context);
        if (moreMenuOpen) {
            drawMoreMenuOverlay(context, mouseX, mouseY, delta);
        }
    }

    private void drawHeader(PoseStack context) {
        fill(context, 0, 0, width, HEADER_HEIGHT, 0xB0101010);
        drawCenteredString(context, font, title, width / 2, 10, 0xFFFFFFFF);
        drawCenteredString(context, font, Component.translatable("screen.universal_config.subtitle"),
                width / 2, 25, MUTED_TEXT_COLOR);
        if (pendingImport != null) {
            int top = pendingTop();
            drawBorderedRect(context, contentLeft(), top, rightPanelRight(), top + PENDING_HEIGHT,
                    PENDING_COLOR, PENDING_BORDER_COLOR, PANEL_BORDER_DARK);
            int actionWidth = pendingButtonWidth(Component.translatable("screen.universal_config.restart_now"))
                    + pendingButtonWidth(Component.translatable("screen.universal_config.apply_scheduled_cancel")) + 6;
            int availableWidth = contentWidth() - actionWidth - 34;
            drawTrimmed(context, Component.translatable("screen.universal_config.pending_named", pendingProfileName()).getString(),
                    contentLeft() + 12, top + 10, availableWidth, WARNING_COLOR);
        }
    }

    private String pendingProfileName() {
        if (pendingImport == null || pendingImport.profilePath == null || pendingImport.profilePath.trim().isEmpty()) {
            return translation("screen.universal_config.this_profile");
        }
        try {
            Path pendingPath = java.nio.file.Paths.get(pendingImport.profilePath).toAbsolutePath().normalize();
            for (ProfileSummary profile : profiles) {
                if (profile.path().toAbsolutePath().normalize().equals(pendingPath)) {
                    return profileName(profile.manifest());
                }
            }
            String fileName = pendingPath.getFileName() == null ? "" : pendingPath.getFileName().toString();
            return fileName.trim().isEmpty() ? translation("screen.universal_config.this_profile") : fileName;
        } catch (RuntimeException ex) {
            return translation("screen.universal_config.this_profile");
        }
    }

    private void drawPanels(PoseStack context) {
        drawBorderedRect(context, contentLeft(), mainTop(), leftPanelRight(), mainBottom(),
                PANEL_COLOR, PANEL_BORDER_LIGHT, PANEL_BORDER_DARK);
        drawBorderedRect(context, rightPanelLeft(), mainTop(), rightPanelRight(), mainBottom(),
                PANEL_COLOR, PANEL_BORDER_LIGHT, PANEL_BORDER_DARK);
    }

    private void drawListContent(PoseStack context) {
        drawPanelHeader(context, contentLeft(), leftPanelRight(), Component.translatable("screen.universal_config.saved_settings"));
        if (profiles.isEmpty()) {
            drawTrimmed(context, translation("screen.universal_config.empty_title"), cardX(), listTop() + 12,
                    cardWidth(), SECONDARY_TEXT_COLOR);
            drawTrimmed(context, translation("screen.universal_config.empty_hint"), cardX(), listTop() + 28,
                    cardWidth(), MUTED_TEXT_COLOR);
        }
    }

    private void drawDetailContent(PoseStack context) {
        ProfileSummary selected = selectedProfile();
        Component heading = selected == null
                ? Component.translatable("screen.universal_config.profile_detail")
                : Component.literal(profileName(selected.manifest()));
        drawPanelHeader(context, rightPanelLeft(), rightPanelRight(), heading);
        int innerTop = detailViewportTop();
        int innerBottom = detailViewportBottom();
        fill(context, detailX(), innerTop, rightPanelRight() - PANEL_PADDING, innerBottom, PANEL_INNER_COLOR);
        if (selected == null) {
            drawTrimmed(context, translation("screen.universal_config.select_profile"), detailX() + 8, innerTop + 12,
                    detailWidth() - 16, MUTED_TEXT_COLOR);
            return;
        }
        ProfileManifest manifest = selected.manifest();
        int x = detailX() + 10;
        int contentWidth = detailContentWidth();
        int y = innerTop + 8 - detailScroll;
        ScreenUtil.enableScissor(detailX(), innerTop, rightPanelRight() - PANEL_PADDING, innerBottom);
        drawProfileIcon(context, x, y, PROFILE_ICON_SIZE, manifest.icon);
        int infoX = x + PROFILE_ICON_SIZE + 10;
        int infoWidth = Math.max(0, x + contentWidth - infoX);
        drawTrimmed(context, environmentSummary(manifest), infoX, y + 1, infoWidth, 0xFFFFFFFF);
        drawTrimmed(context, loaderSummary(manifest), infoX, y + 15, infoWidth, MUTED_TEXT_COLOR);

        y += 31;
        for (FormattedCharSequence line : descriptionLines(manifest)) {
            font.draw(context, line, x, y, SECONDARY_TEXT_COLOR);
            y += DETAIL_LINE_HEIGHT;
        }
        y += 3;
        drawTrimmed(context, updatedSummary(manifest), x, y, contentWidth, MUTED_TEXT_COLOR);
        y += 18;
        // 表示名とは別に、共有ファイルを識別できるよう実際の .ucp ファイル名を表示する。
        drawTrimmed(context, profileFileName(selected), x, y, contentWidth, MUTED_TEXT_COLOR);
        y += 14;
        fill(context, x, y, x + contentWidth, y + 1, DIVIDER_COLOR);
        drawString(context, font, Component.translatable("screen.universal_config.included_settings"),
                x, y + 9, SECONDARY_TEXT_COLOR);
        int itemY = y + 25;
        if (manifest.includes == null) {
            drawTrimmed(context, translation("screen.universal_config.targets_unknown"), x, itemY,
                    contentWidth, MUTED_TEXT_COLOR);
        } else {
            itemY = drawInclude(context, manifest.includes.keybinds, "screen.universal_config.target_keybinds", x, itemY);
            itemY = drawInclude(context, manifest.includes.clientOptions, "screen.universal_config.target_client", x, itemY);
            drawInclude(context, manifest.includes.modConfigs, "screen.universal_config.target_mods", x, itemY);
        }
        ScreenUtil.disableScissor();
    }

    private List<FormattedCharSequence> descriptionLines(ProfileManifest manifest) {
        return font.split(Component.literal(descriptionSummary(manifest)), detailContentWidth());
    }

    private int detailContentHeight(ProfileManifest manifest) {
        int includeLines = Math.max(1, includedSettingCount(manifest));
        return 8 + 31 + descriptionLines(manifest).size() * DETAIL_LINE_HEIGHT + 3 + 18 + 25
                + 14
                + includeLines * 15;
    }

    private String environmentSummary(ProfileManifest manifest) {
        String minecraftVersion = manifest.source == null
                ? translation("screen.universal_config.unknown")
                : safe(manifest.source.minecraftVersion);
        String loader = manifest.source == null
                ? translation("screen.universal_config.unknown")
                : formatLoader(manifest.source.loader);
        return Component.translatable("screen.universal_config.confirm_environment", minecraftVersion, loader).getString();
    }

    private String descriptionSummary(ProfileManifest manifest) {
        return manifest.description == null || manifest.description.trim().isEmpty()
                ? translation("screen.universal_config.description_none")
                : manifest.description;
    }

    private String updatedSummary(ProfileManifest manifest) {
        return formatDate(manifest.updatedAt);
    }

    private String profileFileName(ProfileSummary summary) {
        if (summary == null || summary.path() == null || summary.path().getFileName() == null) {
            return "";
        }
        return summary.path().getFileName().toString();
    }

    private String loaderSummary(ProfileManifest manifest) {
        String loader = manifest.source == null
                ? translation("screen.universal_config.unknown")
                : formatLoader(manifest.source.loader);
        String loaderVersion = manifest.source == null
                ? translation("screen.universal_config.unknown")
                : safe(manifest.source.loaderVersion);
        return Component.translatable("screen.universal_config.loader_summary", loader, loaderVersion).getString();
    }

    private String formatDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return translation("screen.universal_config.date_unknown");
        }
        try {
            return displayDateFormatter().format(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            try {
                return displayDateFormatter().format(OffsetDateTime.parse(value));
            } catch (DateTimeParseException ignoredAgain) {
                return value.length() > 16 ? value.substring(0, 16) : value;
            }
        }
    }

    private String includedSettingsText(ProfileManifest manifest) {
        if (manifest.includes == null) {
            return translation("screen.universal_config.targets_unknown");
        }
        List<String> names = new ArrayList<>();
        if (manifest.includes.keybinds) {
            names.add(translation("screen.universal_config.target_keybinds"));
        }
        if (manifest.includes.clientOptions) {
            names.add(translation("screen.universal_config.target_client"));
        }
        if (manifest.includes.modConfigs) {
            names.add(translation("screen.universal_config.target_mods"));
        }
        return names.isEmpty()
                ? translation("screen.universal_config.targets_none")
                : String.join(translation("screen.universal_config.targets_separator"), names);
    }

    private int includedSettingCount(ProfileManifest manifest) {
        if (manifest.includes == null) {
            return 1;
        }
        int count = 0;
        if (manifest.includes.keybinds) {
            count++;
        }
        if (manifest.includes.clientOptions) {
            count++;
        }
        if (manifest.includes.modConfigs) {
            count++;
        }
        return count;
    }

    private int drawInclude(PoseStack context, boolean included, String key, int x, int y) {
        if (included) {
            drawString(context, font, Component.literal("✓"), x, y, CHECK_COLOR);
            drawTrimmed(context, Component.translatable(key).getString(), x + 14, y, detailWidth() - 34, 0xFFFFFFFF);
            return y + 15;
        }
        return y;
    }

    private void drawPanelHeader(PoseStack context, int left, int right, Component heading) {
        fill(context, left + 2, mainTop() + 2, right - 2, mainTop() + PANEL_HEADER_HEIGHT, PANEL_HEADER_COLOR);
        drawString(context, font, heading, left + PANEL_PADDING, mainTop() + 8, 0xFFFFFFFF);
        fill(context, left + 2, mainTop() + PANEL_HEADER_HEIGHT - 1, right - 2, mainTop() + PANEL_HEADER_HEIGHT, DIVIDER_COLOR);
    }

    private void drawProfileCards(PoseStack context, int mouseX, int mouseY) {
        int firstRow = firstVisibleRow();
        int lastRow = lastVisibleRowExclusive();
        // Cards intentionally remain full-height and are clipped at the viewport boundary. A partially visible
        // next card tells users that more profiles exist without reserving enough space for a complete row.
        ScreenUtil.enableScissor(cardX(), listTop(), cardX() + cardWidth(), listBottom());
        for (int index = firstRow; index < lastRow; index++) {
            int y = cardY(index);
            boolean hovered = inside(mouseX, mouseY, cardX(), y, cardWidth(), CARD_HEIGHT);
            int fill = index == selectedProfileIndex ? SELECTED_CARD_COLOR : hovered ? CARD_HOVER_COLOR : CARD_COLOR;
            int border = index == selectedProfileIndex ? SELECTED_BORDER_COLOR : PANEL_BORDER_DARK;
            drawBorderedRect(context, cardX(), y, cardX() + cardWidth(), y + CARD_HEIGHT, fill, border, PANEL_BORDER_DARK);
            ProfileSummary summary = profiles.get(index);
            ProfileManifest manifest = summary.manifest();
            int iconX = cardX() + 7;
            int iconY = y + (CARD_HEIGHT - PROFILE_ICON_SIZE) / 2;
            drawProfileIcon(context, iconX, iconY, PROFILE_ICON_SIZE, manifest.icon);
            int textX = iconX + PROFILE_ICON_SIZE + 9;
            int textWidth = cardX() + cardWidth() - textX - REORDER_BUTTON_WIDTH - 12;
            int nameWidth = textWidth;
            if (isDefaultProfile(summary.path())) {
                String marker = translation("screen.universal_config.default_marker");
                int markerWidth = Math.min(font.width(marker), textWidth);
                int markerX = textX + textWidth - markerWidth;
                nameWidth = Math.max(0, markerX - textX - 4);
                drawTrimmed(context, marker, markerX, y + 7, markerWidth, SECONDARY_TEXT_COLOR);
            }
            drawTrimmed(context, profileName(manifest), textX, y + 7, nameWidth, 0xFFFFFFFF);
            String[] environment = environmentLines(manifest);
            String version = manifest.source == null ? translation("screen.universal_config.environment_unknown")
                    : safe(manifest.source.minecraftVersion);
            drawTrimmed(context, version + " " + environment[1], textX, y + 23, textWidth, SECONDARY_TEXT_COLOR);
        }
        ScreenUtil.disableScissor();
    }

    private void drawDragIndicator(PoseStack context) {
        if (!draggingProfile || dragTargetIndex < 0 || dragTargetIndex == draggingProfileIndex) {
            return;
        }
        int y = dragTargetIndex > draggingProfileIndex
                ? cardY(dragTargetIndex) + CARD_HEIGHT - 1
                : cardY(dragTargetIndex) - 1;
        fill(context, cardX(), y, cardX() + cardWidth(), y + 2, SELECTED_BORDER_COLOR);
    }

    private void drawReorderButtons(PoseStack context, int mouseX, int mouseY, float delta) {
        for (Button button : reorderButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
    }

    private void drawProfileIcon(PoseStack context, int x, int y, int size, String iconId) {
        ItemStack stack = ScreenUtil.iconStack(iconId);
        float scale = size / 16.0F;
        // 1.16's ItemRenderer does not consume this screen's PoseStack. Transforming only the context leaves the
        // item at the unscaled origin, where the panel scissor clips it completely. Use the legacy RenderSystem model
        // view stack so both list-card and detail icons are rendered at their intended coordinates.
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.translate(x, y, 0.0F);
            modelView.scale(scale, scale, 1.0F);
            RenderSystem.applyModelViewMatrix();
            minecraft.getItemRenderer().renderAndDecorateItem(stack, 0, 0);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private DateTimeFormatter displayDateFormatter() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected().getCode();
        String languageCode = language == null ? "" : language;
        if (languageCode.equals(dateFormatterLanguage) && dateFormatter != null) {
            return dateFormatter;
        }

        Locale locale = localeForLanguage(languageCode);
        String pattern = translation("screen.universal_config.date_format");
        try {
            dateFormatter = DateTimeFormatter.ofPattern(pattern, locale)
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ex) {
            FileOperationLogger.failure("CREATE_DATE_FORMATTER", null,
                    "invalid date pattern for language " + languageCode + "; using fallback", ex);
            dateFormatter = DateTimeFormatter.ofPattern(SAFE_DATE_PATTERN, locale)
                    .withZone(ZoneId.systemDefault());
        }
        dateFormatterLanguage = languageCode;
        return dateFormatter;
    }

    private Locale localeForLanguage(String languageCode) {
        if (languageCode.trim().isEmpty()) {
            return Locale.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(languageCode.replace('_', '-'));
        return locale.getLanguage().trim().isEmpty() ? Locale.ENGLISH : locale;
    }

    private boolean isDefaultProfile(Path profilePath) {
        return defaultProfilePath != null
                && profilePath != null
                && defaultProfilePath.equals(profilePath.toAbsolutePath().normalize());
    }

    private void drawScrollbar(PoseStack context) {
        int maxScroll = maxListScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = listTop();
        int trackBottom = listBottom();
        int trackHeight = trackBottom - trackTop;
        int contentHeight = profiles.size() * CARD_STEP - CARD_GAP;
        int thumbHeight = Math.max(14, trackHeight * trackHeight / contentHeight);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * listScroll / maxScroll;
        int x = leftPanelRight() - PANEL_PADDING - SCROLLBAR_WIDTH;
        fill(context, x, trackTop, x + SCROLLBAR_WIDTH, trackBottom, 0xFF303030);
        fill(context, x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFFAAAAAA);
    }

    private void drawDetailScrollbar(PoseStack context) {
        int maxScroll = maxDetailScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackTop = detailViewportTop();
        int trackBottom = detailViewportBottom();
        int trackHeight = trackBottom - trackTop;
        int contentHeight = detailContentHeight(selectedProfile().manifest());
        int thumbHeight = Math.max(14, trackHeight * trackHeight / contentHeight);
        int thumbTop = trackTop + (trackHeight - thumbHeight) * detailScroll / maxScroll;
        int x = rightPanelRight() - PANEL_PADDING - SCROLLBAR_WIDTH;
        fill(context, x, trackTop, x + SCROLLBAR_WIDTH, trackBottom, 0xFF303030);
        fill(context, x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFFAAAAAA);
    }

    private void drawMoreMenuOverlay(PoseStack context, int mouseX, int mouseY, float delta) {
        // Screen children are rendered at Minecraft's widget depth. Redrawing the menu after every base layer
        // prevents detail text and dividers from leaking over this temporary top-level surface.
        context.pushPose();
        context.translate(0.0F, 0.0F, 300.0F);
        drawBorderedRect(context, menuX(), menuY(), menuX() + MENU_WIDTH, menuY() + MENU_HEIGHT,
                MENU_COLOR, PANEL_BORDER_LIGHT, PANEL_BORDER_DARK);
        for (Button button : moreMenuButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        context.popPose();
    }

    private void drawStatus(PoseStack context) {
        if (status.getString().isEmpty()) {
            return;
        }
        drawTrimmed(context, status.getString(), contentLeft(), footerButtonY() - 11, contentWidth(),
                status.getStyle().getColor() == null ? MUTED_TEXT_COLOR : status.getStyle().getColor().getValue());
    }

    private String[] environmentLines(ProfileManifest manifest) {
        if (manifest.source == null) {
            return new String[]{translation("screen.universal_config.environment_unknown"), ""};
        }
        return new String[]{Component.translatable("screen.universal_config.minecraft_version",
                safe(manifest.source.minecraftVersion)).getString(), formatLoader(manifest.source.loader)};
    }

    private String formatLoader(String value) {
        String loader = safe(value);
        if (loader.isEmpty()) {
            return loader;
        }
        if ("fabric".equalsIgnoreCase(loader)) return "Fabric";
        if ("forge".equalsIgnoreCase(loader)) return "Forge";
        if ("neoforge".equalsIgnoreCase(loader)) return "NeoForge";
        return loader.substring(0, 1).toUpperCase() + loader.substring(1);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? translation("screen.universal_config.unknown") : value;
    }

    private String profileName(ProfileManifest manifest) {
        return manifest == null || manifest.name == null || manifest.name.trim().isEmpty()
                ? translation("screen.universal_config.this_profile")
                : manifest.name;
    }

    private String translation(String key) {
        return Component.translatable(key).getString();
    }

    private void drawBorderedRect(PoseStack context, int left, int top, int right, int bottom,
                                  int fill, int lightBorder, int darkBorder) {
        fill(context, left, top, right, bottom, darkBorder);
        fill(context, left + 1, top + 1, right - 1, bottom - 1, lightBorder);
        fill(context, left + 2, top + 2, right - 2, bottom - 2, fill);
    }

    private void drawTrimmed(PoseStack context, String text, int x, int y, int maxWidth, int color) {
        drawString(context, font,
                font.plainSubstrByWidth(text == null ? "" : text, Math.max(0, maxWidth)), x, y, color);
    }

    private static final class ProfileCardButton extends Button {
        private ProfileCardButton(int x, int y, int width, int height, MutableComponent narration, Button.OnPress onPress) {
            super(x, y, width, height, narration, onPress);
        }

        @Override
        public void renderButton(PoseStack context, int mouseX, int mouseY, float delta) {
            // Profile cards are drawn once by the screen and clipped as a group. Rendering the vanilla nine-slice
            // for a partially visible click target can divide by zero on 1.20.1 when only a few pixels remain.
        }
    }
}
