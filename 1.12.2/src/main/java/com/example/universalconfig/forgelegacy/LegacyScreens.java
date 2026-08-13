package com.example.universalconfig.forgelegacy;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import com.example.universalconfig.core.ProfileCreateOptions;
import com.example.universalconfig.core.ProfileDiff;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.ProfileSummary;
import com.example.universalconfig.core.RiskLevel;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forge 1.7-1.12の共通GUI。固定解像度を前提にせず、表示行数とボタン幅を毎回画面寸法から求める。
 * 新GUIと同じコアAPIだけを呼ぶため、プロファイル形式・安全検証・初回適用・バックアップ動作は世代間で一致する。
 */
public final class LegacyScreens {
    private static final int BUTTON_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 46;
    private static final int PANEL_HEADER_HEIGHT = 24;
    private static final int PANEL_GAP = 8;
    private static final int PANEL_PADDING = 8;
    private static final int CARD_HEIGHT = 40;
    private static final int CARD_GAP = 3;
    private static final int CARD_STEP = CARD_HEIGHT + CARD_GAP;
    private static final int PROFILE_ICON_SIZE = 28;
    private static final int MORE_MENU_WIDTH = 132;
    private static final int MORE_MENU_ITEM_GAP = 2;
    private static final int MORE_MENU_ITEM_COUNT = 6;
    private static final int REORDER_BUTTON_WIDTH = 20;
    private static final int REORDER_BUTTON_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int PANEL = 0xD0181818;
    private static final int PANEL_INNER = 0xB00B0B0B;
    private static final int PANEL_HEADER = 0xD02C2C2C;
    private static final int CARD = 0xFF303030;
    private static final int CARD_HOVER = 0xFF3A3A3A;
    private static final int SELECTED = 0xFF465064;
    private static final int BORDER = 0xFF6E6E6E;
    private static final int DIVIDER = 0xFF494949;
    private static final int TEXT = 0xFFFFFF;
    private static final int MUTED = 0xB0B0B0;
    private static final int ERROR = 0xFF7777;

    private LegacyScreens() {
    }

    private static String tr(String key, Object... values) {
        return I18n.format(key, values);
    }

    private static GuiButton createButton(int id, int x, int y, int width, int height, String label) {
        return LegacyVersionBridge.button(id, x, y, width, height, label);
    }

    private abstract static class Base extends GuiScreen {
        protected final GuiScreen parent;
        protected String status = "";

        protected Base(GuiScreen parent) {
            this.parent = parent;
        }

        protected ProfileService service() throws UniversalConfigException {
            return LegacyPlatform.service();
        }

        protected void fail(String key, Exception ex) {
            status = tr(key);
            // UI failures must remain actionable on-screen while retaining diagnostics for launcher-based reports.
            try {
                FileOperationLogger.failure("LEGACY_UI", LegacyPlatform.gameDirectory(), key, ex);
            } catch (RuntimeException ignored) {
                // Logging must never replace the original user-visible error with a second failure.
            }
        }

        @Override
        public void onGuiClosed() {
            Keyboard.enableRepeatEvents(false);
        }

        protected void back() {
            mc.displayGuiScreen(parent);
        }

        protected void drawStatus() {
            if (!status.isEmpty()) {
                drawCenteredString(font(), status, width / 2, height - 12, ERROR);
            }
        }

        protected FontRenderer font() {
            return LegacyVersionBridge.font(mc);
        }

        protected int contentWidth() {
            return Math.max(240, Math.min(760, width - 24));
        }

        protected int contentLeft() {
            return (width - contentWidth()) / 2;
        }
    }

    public static class ProfileList extends Base implements GuiYesNoCallback {
        private List<ProfileSummary> profiles = Collections.emptyList();
        private int selected = -1;
        private int scroll;
        private Path defaultProfile;
        private boolean pending;
        private boolean moreMenuOpen;
        private Path deleteConfirmationPath;
        private boolean draggingScrollbar;
        private int scrollbarDragOffset;
        private int draggingProfileIndex = -1;
        private int dragTargetIndex = -1;
        private int dragStartY;
        private boolean draggingProfile;

        public ProfileList(GuiScreen parent) {
            super(parent);
        }

        @Override
        public void initGui() {
            buttonList.clear();
            load();
            int left = contentLeft();
            buttonList.add(createButton(1, left, height - 28, contentWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.save_current")));
            int actionY = mainBottom() - 28;
            int actionArea = rightPanelRight() - rightPanelLeft() - PANEL_PADDING * 2;
            int moreWidth = 34;
            int useWidth = Math.min(160, Math.max(70, actionArea - moreWidth - 4));
            int actionX = rightPanelLeft() + Math.max(PANEL_PADDING, (actionArea - useWidth - moreWidth - 4) / 2 + PANEL_PADDING);
            buttonList.add(createButton(2, actionX, actionY, useWidth, BUTTON_HEIGHT,
                    tr("screen.universal_config.use_profile")));
            buttonList.add(createButton(4, actionX + useWidth + 4, actionY, moreWidth, BUTTON_HEIGHT,
                    tr("screen.universal_config.more")));
            // moreMenuの項目順序は main 側の ProfileListScreen に合わせる。
            // default / open_folder / rename / duplicate / backups / delete の6項目。
            buttonList.add(createButton(30, moreMenuLeft(), moreMenuButtonY(0), moreMenuWidth(), BUTTON_HEIGHT, ""));
            buttonList.add(createButton(31, moreMenuLeft(), moreMenuButtonY(1), moreMenuWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.open_folder")));
            buttonList.add(createButton(32, moreMenuLeft(), moreMenuButtonY(2), moreMenuWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.rename")));
            buttonList.add(createButton(33, moreMenuLeft(), moreMenuButtonY(3), moreMenuWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.duplicate")));
            buttonList.add(createButton(34, moreMenuLeft(), moreMenuButtonY(4), moreMenuWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.backups")));
            buttonList.add(createButton(35, moreMenuLeft(), moreMenuButtonY(5), moreMenuWidth(), BUTTON_HEIGHT,
                    tr("screen.universal_config.delete")));
            buttonList.add(createButton(0, width - 28, 6, 20, BUTTON_HEIGHT, "×"));
            // 並べ替えボタン（↑/↓）は表示行ごとに追加する。scroll 後の位置を再計算できるように
            // initGui の末尾で構築し、cardWidth の右端に配置する（PR #43）。
            addReorderButtons();
            if (pending) {
                int y = HEADER_HEIGHT + 5;
                buttonList.add(createButton(9, rightPanelRight() - 194, y, 92, BUTTON_HEIGHT,
                        tr("screen.universal_config.restart_now")));
                buttonList.add(createButton(10, rightPanelRight() - 98, y, 92, BUTTON_HEIGHT,
                        tr("screen.universal_config.apply_scheduled_cancel")));
            }
            updateButtons();
        }

        private void load() {
            try {
                Path old = selectedPath();
                ProfileService service = service();
                profiles = service.listProfiles();
                PendingImport pendingImport = service.readPendingImport(LegacyPlatform.gameDirectory());
                pending = pendingImport != null;
                defaultProfile = service.resolveDefaultProfile(LegacyPlatform.gameDirectory());
                selected = -1;
                if (old != null) {
                    for (int i = 0; i < profiles.size(); i++) {
                        if (profiles.get(i).path().equals(old)) selected = i;
                    }
                }
                if (selected < 0 && !profiles.isEmpty()) selected = 0;
                clampScroll();
                status = "";
            } catch (Exception ex) {
                profiles = Collections.emptyList();
                selected = -1;
                defaultProfile = null;
                fail("screen.universal_config.load_failed", ex);
            }
        }

        private Path selectedPath() {
            return selected >= 0 && selected < profiles.size() ? profiles.get(selected).path() : null;
        }

        private int mainTop() {
            return HEADER_HEIGHT + (pending ? 34 : 0);
        }

        private int mainBottom() {
            return height - 34;
        }

        private int leftPanelRight() {
            return contentLeft() + (contentWidth() - PANEL_GAP) * 49 / 100;
        }

        private int rightPanelLeft() {
            return leftPanelRight() + PANEL_GAP;
        }

        private int rightPanelRight() {
            return contentLeft() + contentWidth();
        }

        private int listTop() {
            return mainTop() + PANEL_HEADER_HEIGHT + 7;
        }

        private int listBottom() {
            return mainBottom() - PANEL_PADDING;
        }

        private int cardLeft() {
            return contentLeft() + PANEL_PADDING;
        }

        private int cardWidth() {
            return leftPanelRight() - contentLeft() - PANEL_PADDING * 2
                    - (hasScrollbar() ? SCROLLBAR_WIDTH + 5 : 0);
        }

        private int visibleRows() {
            return Math.max(1, (listBottom() - listTop() + CARD_GAP) / CARD_STEP);
        }

        private void clampScroll() {
            scroll = Math.max(0, Math.min(scroll, Math.max(0, profiles.size() - visibleRows())));
        }

        private boolean hasScrollbar() {
            return profiles.size() > visibleRows();
        }

        private int scrollbarLeft() {
            return leftPanelRight() - PANEL_PADDING - SCROLLBAR_WIDTH;
        }

        private int scrollbarThumbHeight() {
            int trackHeight = listBottom() - listTop();
            return Math.max(16, trackHeight * visibleRows() / Math.max(1, profiles.size()));
        }

        private int scrollbarThumbTop() {
            int available = listBottom() - listTop() - scrollbarThumbHeight();
            int maxScroll = Math.max(1, profiles.size() - visibleRows());
            return listTop() + available * scroll / maxScroll;
        }

        private void scrollToThumbPosition(int mouseY) {
            int available = listBottom() - listTop() - scrollbarThumbHeight();
            if (available <= 0) {
                scroll = 0;
                return;
            }
            int position = Math.max(0, Math.min(available, mouseY - listTop() - scrollbarDragOffset));
            int maxScroll = Math.max(0, profiles.size() - visibleRows());
            scroll = Math.round(position * maxScroll / (float) available);
            clampScroll();
        }

        private void updateButtons() {
            boolean has = selectedPath() != null;
            for (Object value : buttonList) {
                GuiButton button = (GuiButton) value;
                if (button.id == 2 || button.id == 4) button.enabled = has;
                if (button.id >= 30 && button.id <= 35) {
                    button.visible = moreMenuOpen;
                    button.enabled = has;
                }
            }
            GuiButton defaultButton = findButton(30);
            if (defaultButton != null) {
                defaultButton.displayString = tr(has && isDefault(selectedPath())
                        ? "screen.universal_config.clear_default" : "screen.universal_config.set_default");
            }
        }

        private GuiButton findButton(int id) {
            for (Object value : buttonList) {
                GuiButton button = (GuiButton) value;
                if (button.id == id) return button;
            }
            return null;
        }

        private void addReorderButtons() {
            // 並べ替えボタン（ID 40=↑, 41=↓）は cardWidth の右端に配置し、
            // 表示行ごとに初期化する。scroll 変化で位置を再計算するため initGui 末尾で呼ぶ（PR #43）。
            for (int i = scroll; i < Math.min(profiles.size(), scroll + visibleRows()); i++) {
                int y = listTop() + (i - scroll) * CARD_STEP;
                int x = cardLeft() + cardWidth() - REORDER_BUTTON_WIDTH - 4;
                GuiButton up = createButton(40, x, y + 3, REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT, "↑");
                up.enabled = i > 0;
                GuiButton down = createButton(41, x, y + 3 + REORDER_BUTTON_HEIGHT + 1,
                        REORDER_BUTTON_WIDTH, REORDER_BUTTON_HEIGHT, "↓");
                down.enabled = i < profiles.size() - 1;
                buttonList.add(up);
                buttonList.add(down);
            }
        }

        private void moveProfile(int sourceIndex, int targetIndex) {
            // main 側の ProfileListScreen.moveProfile に合わせ、範囲外・同一インデックスは無視する。
            if (sourceIndex < 0 || sourceIndex >= profiles.size()
                    || targetIndex < 0 || targetIndex >= profiles.size()
                    || sourceIndex == targetIndex) {
                return;
            }
            Path path = profiles.get(sourceIndex).path();
            try {
                service().moveProfile(LegacyPlatform.gameDirectory(), path, targetIndex);
                initGui();
                selectProfile(path);
            } catch (Exception ex) {
                fail("screen.universal_config.action_failed", ex);
            }
        }

        private void selectProfile(Path path) {
            // 並び替え前のインデックスは再読込後には別プロフィールを指す場合がある。
            // 操作対象を維持するため、安定したファイルパスで移動元を選択し直す。
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).path().equals(path)) {
                    selected = i;
                    updateButtons();
                    return;
                }
            }
        }

        private int moreMenuWidth() {
            return Math.min(MORE_MENU_WIDTH, rightPanelRight() - rightPanelLeft() - PANEL_PADDING * 2);
        }

        private int moreMenuLeft() {
            return rightPanelRight() - PANEL_PADDING - moreMenuWidth();
        }

        private int moreMenuBottom() {
            return mainBottom() - 32;
        }

        private int moreMenuTop() {
            return moreMenuBottom() - (MORE_MENU_ITEM_COUNT * BUTTON_HEIGHT
                    + (MORE_MENU_ITEM_COUNT - 1) * MORE_MENU_ITEM_GAP + 8);
        }

        private int moreMenuButtonY(int index) {
            return moreMenuTop() + 4 + index * (BUTTON_HEIGHT + MORE_MENU_ITEM_GAP);
        }

        private int moreButtonLeft() {
            int actionArea = rightPanelRight() - rightPanelLeft() - PANEL_PADDING * 2;
            int useWidth = Math.min(160, Math.max(70, actionArea - 38));
            int actionX = rightPanelLeft() + Math.max(PANEL_PADDING,
                    (actionArea - useWidth - 38) / 2 + PANEL_PADDING);
            return actionX + useWidth + 4;
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            Path path = selectedPath();
            try {
                switch (button.id) {
                    case 0: back(); break;
                    case 1: mc.displayGuiScreen(new ProfileCreate(this)); break;
                    case 2: if (path != null) mc.displayGuiScreen(new ConfirmApply(this, path)); break;
                    case 4:
                        if (path != null) {
                            moreMenuOpen = !moreMenuOpen;
                        }
                        break;
                    case 30:
                        if (path != null) {
                            ProfileService service = service();
                            if (service.isDefaultProfile(path)) service.clearDefaultProfile(LegacyPlatform.gameDirectory());
                            else service.setDefaultProfile(LegacyPlatform.gameDirectory(), path);
                            moreMenuOpen = false;
                            initGui();
                        }
                        break;
                    case 31:
                        if (path != null) Desktop.getDesktop().browse(path.getParent().toUri());
                        moreMenuOpen = false;
                        break;
                    case 32:
                        if (path != null) {
                            moreMenuOpen = false;
                            mc.displayGuiScreen(new ProfileRename(this, path, profileName()));
                        }
                        break;
                    case 33:
                        if (path != null) {
                            service().duplicateProfile(path);
                            moreMenuOpen = false;
                            initGui();
                        }
                        break;
                    case 34:
                        moreMenuOpen = false;
                        mc.displayGuiScreen(new Backups(this));
                        break;
                    case 35:
                        if (path != null) {
                            deleteConfirmationPath = path;
                            moreMenuOpen = false;
                            mc.displayGuiScreen(new GuiYesNo(this,
                                    tr("screen.universal_config.delete_confirm", profileName()),
                                    tr("screen.universal_config.delete_warning"), 35));
                        }
                        break;
                    case 40: moveProfile(selected, selected - 1); break;
                    case 41: moveProfile(selected, selected + 1); break;
                    case 9: restart(); break;
                    case 10: service().clearPendingImport(LegacyPlatform.gameDirectory()); initGui(); break;
                    default: break;
                }
            } catch (Exception ex) {
                fail("screen.universal_config.action_failed", ex);
            }
            updateButtons();
        }

        private String profileName() {
            if (selected < 0 || selected >= profiles.size()) return tr("screen.universal_config.this_profile");
            String value = profiles.get(selected).manifest().name;
            return value == null || value.trim().isEmpty() ? profiles.get(selected).path().getFileName().toString() : value;
        }

        private void restart() {
            try {
                LegacyPlatform.scheduleRestart();
                LegacyVersionBridge.shutdown(mc);
            } catch (Exception ex) {
                fail("screen.universal_config.restart_failed_title", ex);
            }
        }

        @Override
        public void confirmClicked(boolean result, int id) {
            mc.displayGuiScreen(this);
            if (result && id == 35 && deleteConfirmationPath != null) {
                try {
                    service().deleteProfile(LegacyPlatform.gameDirectory(), deleteConfirmationPath);
                    initGui();
                } catch (Exception ex) {
                    fail("screen.universal_config.delete_failed", ex);
                }
            }
            deleteConfirmationPath = null;
        }

        @Override
        public void handleMouseInput() {
            int eventButton = Mouse.getEventButton();
            boolean leftReleased = eventButton == 0 && !Mouse.getEventButtonState();
            try { super.handleMouseInput(); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
            if (leftReleased && draggingProfileIndex >= 0) {
                finishProfileDrag(Mouse.getEventX() * width / mc.displayWidth,
                        height - Mouse.getEventY() * height / mc.displayHeight - 1);
            }
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0) {
                scroll += wheel < 0 ? 1 : -1;
                clampScroll();
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            draggingScrollbar = false;
            draggingProfileIndex = -1;
            draggingProfile = false;
            if (mouseButton == 0 && hasScrollbar()
                    && mouseX >= scrollbarLeft() - 2 && mouseX < scrollbarLeft() + SCROLLBAR_WIDTH + 2
                    && mouseY >= listTop() && mouseY < listBottom()) {
                int thumbTop = scrollbarThumbTop();
                int thumbBottom = thumbTop + scrollbarThumbHeight();
                if (mouseY >= thumbTop && mouseY < thumbBottom) {
                    draggingScrollbar = true;
                    scrollbarDragOffset = mouseY - thumbTop;
                } else {
                    scrollbarDragOffset = scrollbarThumbHeight() / 2;
                    scrollToThumbPosition(mouseY);
                    draggingScrollbar = true;
                }
                return;
            }
            boolean insideMenu = mouseX >= moreMenuLeft() && mouseX < moreMenuLeft() + moreMenuWidth()
                    && mouseY >= moreMenuTop() && mouseY < moreMenuBottom();
            boolean insideMoreButton = mouseX >= moreButtonLeft() && mouseX < moreButtonLeft() + 34
                    && mouseY >= mainBottom() - 28 && mouseY < mainBottom() - 8;
            if (moreMenuOpen && !insideMenu && !insideMoreButton) {
                moreMenuOpen = false;
                updateButtons();
            }
            if (mouseButton == 0) {
                int profileIndex = profileIndexAt(mouseX, mouseY);
                int reorderDelta = reorderDeltaAt(mouseX, mouseY);
                if (profileIndex >= 0 && reorderDelta != 0) {
                    moveProfile(profileIndex, profileIndex + reorderDelta);
                    return;
                }
                if (profileIndex >= 0) {
                    draggingProfileIndex = profileIndex;
                    dragTargetIndex = profileIndex;
                    dragStartY = mouseY;
                    return;
                }
            }
            try { super.mouseClicked(mouseX, mouseY, mouseButton); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            if (draggingScrollbar && clickedMouseButton == 0) {
                scrollToThumbPosition(mouseY);
                return;
            }
            if (draggingProfileIndex >= 0 && clickedMouseButton == 0) {
                if (!draggingProfile && Math.abs(mouseY - dragStartY) > 3) {
                    draggingProfile = true;
                }
                if (draggingProfile) {
                    dragTargetIndex = dropIndexAt(mouseY);
                }
                return;
            }
            try {
                super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            } catch (Exception ex) {
                fail("screen.universal_config.action_failed", ex);
            }
        }

        private void finishProfileDrag(int mouseX, int mouseY) {
            int sourceIndex = draggingProfileIndex;
            int targetIndex = dragTargetIndex;
            boolean wasDragging = draggingProfile;
            draggingProfileIndex = -1;
            dragTargetIndex = -1;
            draggingProfile = false;
            if (wasDragging) {
                moveProfile(sourceIndex, targetIndex);
            } else if (profileIndexAt(mouseX, mouseY) == sourceIndex) {
                selected = sourceIndex;
                updateButtons();
            }
        }

        private int profileIndexAt(int mouseX, int mouseY) {
            if (mouseX < cardLeft() || mouseX >= cardLeft() + cardWidth()
                    || mouseY < listTop() || mouseY >= listBottom()) {
                return -1;
            }
            int rowOffset = mouseY - listTop();
            int index = scroll + rowOffset / CARD_STEP;
            return index >= 0 && index < profiles.size() && rowOffset % CARD_STEP < CARD_HEIGHT ? index : -1;
        }

        private int reorderDeltaAt(int mouseX, int mouseY) {
            int index = profileIndexAt(mouseX, mouseY);
            if (index < 0) return 0;
            int x = cardLeft() + cardWidth() - REORDER_BUTTON_WIDTH - 4;
            int y = listTop() + (index - scroll) * CARD_STEP + 3;
            if (mouseX >= x && mouseX < x + REORDER_BUTTON_WIDTH
                    && mouseY >= y && mouseY < y + REORDER_BUTTON_HEIGHT) return -1;
            int downY = y + REORDER_BUTTON_HEIGHT + 1;
            return mouseX >= x && mouseX < x + REORDER_BUTTON_WIDTH
                    && mouseY >= downY && mouseY < downY + REORDER_BUTTON_HEIGHT ? 1 : 0;
        }

        private int dropIndexAt(int mouseY) {
            int relativeY = mouseY - listTop() - CARD_HEIGHT / 2;
            int index = scroll + relativeY / CARD_STEP;
            return Math.max(0, Math.min(profiles.size() - 1, index));
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.title"), width / 2, 10, TEXT);
            drawCenteredString(font(), tr("screen.universal_config.subtitle"), width / 2, 25, MUTED);
            if (pending) {
                drawRect(contentLeft(), HEADER_HEIGHT, rightPanelRight(), HEADER_HEIGHT + 30, 0xE03B321B);
                drawString(font(), tr("screen.universal_config.apply_scheduled_title"), contentLeft() + 10,
                        HEADER_HEIGHT + 11, 0xFFD34E);
            }
            drawPanel(contentLeft(), leftPanelRight(), tr("screen.universal_config.saved_settings"));
            drawPanel(rightPanelLeft(), rightPanelRight(), selected < 0
                    ? tr("screen.universal_config.profile_detail") : profileName());
            int end = Math.min(profiles.size(), scroll + visibleRows());
            for (int i = scroll; i < end; i++) {
                int y = listTop() + (i - scroll) * CARD_STEP;
                boolean hovered = mouseX >= cardLeft() && mouseX < cardLeft() + cardWidth()
                        && mouseY >= y && mouseY < y + CARD_HEIGHT;
                drawRect(cardLeft(), y, cardLeft() + cardWidth(), y + CARD_HEIGHT,
                        i == selected ? SELECTED : hovered ? CARD_HOVER : CARD);
                ProfileManifest manifest = profiles.get(i).manifest();
                String name = manifest.name == null || manifest.name.trim().isEmpty()
                        ? profiles.get(i).path().getFileName().toString() : manifest.name;
                int iconX = cardLeft() + 7;
                int iconY = y + (CARD_HEIGHT - PROFILE_ICON_SIZE) / 2;
                LegacyVersionBridge.drawProfileIcon(mc, manifest.icon, iconX, iconY, PROFILE_ICON_SIZE);
                int textX = iconX + PROFILE_ICON_SIZE + 8;
                int textWidth = cardLeft() + cardWidth() - textX - REORDER_BUTTON_WIDTH - 12;
                if (isDefault(profiles.get(i).path())) {
                    String marker = tr("screen.universal_config.default_marker");
                    int markerWidth = Math.min(font().getStringWidth(marker), textWidth);
                    drawString(font(), marker, textX + textWidth - markerWidth, y + 7, MUTED);
                    textWidth = Math.max(0, textWidth - markerWidth - 4);
                }
                drawString(font(), font().trimStringToWidth(name, textWidth), textX, y + 7, TEXT);
                String version = manifest.source == null ? "" : manifest.source.minecraftVersion;
                String loader = manifest.source == null ? "" : manifest.source.loader;
                String summary = safe(version) + (safe(loader).isEmpty() ? "" : " / " + safe(loader));
                drawString(font(), font().trimStringToWidth(summary, textWidth), textX, y + 23, MUTED);
            }
            if (profiles.isEmpty()) {
                drawString(font(), font().trimStringToWidth(tr("screen.universal_config.empty_title"), cardWidth()),
                        cardLeft(), listTop() + 10, MUTED);
                drawString(font(), font().trimStringToWidth(tr("screen.universal_config.empty_hint"), cardWidth()),
                        cardLeft(), listTop() + 26, MUTED);
            }
            if (hasScrollbar()) {
                drawRect(scrollbarLeft(), listTop(), scrollbarLeft() + SCROLLBAR_WIDTH, listBottom(), 0xFF242424);
                int thumbTop = scrollbarThumbTop();
                drawRect(scrollbarLeft(), thumbTop, scrollbarLeft() + SCROLLBAR_WIDTH,
                        thumbTop + scrollbarThumbHeight(), 0xFF9A9A9A);
            }
            drawDetails();
            if (moreMenuOpen) {
                drawRect(moreMenuLeft() - 4, moreMenuTop(), moreMenuLeft() + moreMenuWidth() + 4,
                        moreMenuBottom(), 0xFF181818);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            if (draggingProfile && dragTargetIndex >= 0 && dragTargetIndex != draggingProfileIndex) {
                int indicatorY = listTop() + (dragTargetIndex - scroll) * CARD_STEP
                        + (dragTargetIndex > draggingProfileIndex ? CARD_HEIGHT - 1 : -1);
                drawRect(cardLeft(), indicatorY, cardLeft() + cardWidth(), indicatorY + 2, 0xFFD8E3FF);
            }
            drawStatus();
        }

        private void drawPanel(int left, int right, String heading) {
            drawRect(left, mainTop(), right, mainBottom(), PANEL);
            drawRect(left + 2, mainTop() + 2, right - 2, mainTop() + PANEL_HEADER_HEIGHT, PANEL_HEADER);
            drawRect(left + 2, mainTop() + PANEL_HEADER_HEIGHT - 1, right - 2,
                    mainTop() + PANEL_HEADER_HEIGHT, DIVIDER);
            drawString(font(), font().trimStringToWidth(heading, right - left - PANEL_PADDING * 2),
                    left + PANEL_PADDING, mainTop() + 8, TEXT);
        }

        private void drawDetails() {
            int x = rightPanelLeft() + PANEL_PADDING;
            int availableWidth = rightPanelRight() - x - PANEL_PADDING;
            int top = mainTop() + PANEL_HEADER_HEIGHT + 7;
            int bottom = mainBottom() - 34;
            drawRect(x, top, x + availableWidth, bottom, PANEL_INNER);
            if (selected < 0 || selected >= profiles.size() || availableWidth < 80) {
                drawString(font(), font().trimStringToWidth(tr("screen.universal_config.select_profile"), availableWidth - 12),
                        x + 6, top + 10, MUTED);
                return;
            }
            ProfileManifest manifest = profiles.get(selected).manifest();
            int innerX = x + 8;
            int contentWidth = availableWidth - 16;
            int line = top + 8;
            LegacyVersionBridge.drawProfileIcon(mc, manifest.icon, innerX, line, PROFILE_ICON_SIZE);
            int infoX = innerX + PROFILE_ICON_SIZE + 8;
            String version = manifest.source == null ? tr("screen.universal_config.unknown") : safe(manifest.source.minecraftVersion);
            String loader = manifest.source == null ? tr("screen.universal_config.unknown") : safe(manifest.source.loader);
            drawString(font(), font().trimStringToWidth(tr("screen.universal_config.confirm_environment", version, loader),
                    contentWidth - PROFILE_ICON_SIZE - 8), infoX, line + 1, TEXT);
            String loaderVersion = manifest.source == null ? tr("screen.universal_config.unknown") : safe(manifest.source.loaderVersion);
            drawString(font(), font().trimStringToWidth(tr("screen.universal_config.loader_summary", loader, loaderVersion),
                    contentWidth - PROFILE_ICON_SIZE - 8), infoX, line + 15, MUTED);
            line += 35;
            // 詳細画面に .ucp ファイル名を表示し、複数プロファイルで実体を区別できるようにする（PR #43）。
            String fileName = profiles.get(selected).path().getFileName().toString();
            drawString(font(), font().trimStringToWidth(fileName, contentWidth), innerX, line, MUTED);
            line += 12;
            String description = manifest.description == null || manifest.description.trim().isEmpty()
                    ? tr("screen.universal_config.description_none") : manifest.description;
            for (Object value : font().listFormattedStringToWidth(description, contentWidth)) {
                String text = String.valueOf(value);
                drawString(font(), text, innerX, line, MUTED);
                line += 11;
                if (line > bottom - 62) break;
            }
            if (manifest.includes != null) {
                line += 5;
                if (line + font().FONT_HEIGHT < bottom) {
                    drawRect(innerX, line, innerX + contentWidth, line + 1, DIVIDER);
                    line += 8;
                    drawString(font(), tr("screen.universal_config.included_settings"), innerX, line, TEXT);
                    line += 14;
                    line = drawIncluded(manifest.includes.keybinds, "screen.universal_config.target_keybinds",
                            innerX, line, bottom);
                    line = drawIncluded(manifest.includes.clientOptions, "screen.universal_config.target_client",
                            innerX, line, bottom);
                    drawIncluded(manifest.includes.modConfigs, "screen.universal_config.target_mods",
                            innerX, line, bottom);
                }
            }
        }

        private int drawIncluded(boolean included, String key, int x, int y, int bottom) {
            // 小さいウィンドウでは詳細欄の下に操作ボタンがある。内容をボタン領域へ描画しない。
            if (included && y + font().FONT_HEIGHT <= bottom) {
                drawString(font(), "✓", x, y, 0x55FF55);
                drawString(font(), tr(key), x + 14, y, TEXT);
                return y + 14;
            }
            return y;
        }

        private boolean isDefault(Path path) {
            return path != null && defaultProfile != null
                    && path.toAbsolutePath().normalize().equals(defaultProfile.toAbsolutePath().normalize());
        }

        private String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class ProfileRename extends Base {
        // main 側の ProfileRenameScreen を旧Forgeへ移植した画面。GuiTextField で新しい名前を入力し、
        // ProfileService.renameProfile でマニフェストを更新する（PR #43）。
        private static final int FORM_WIDTH = 300;
        private static final int FIELD_Y = 64;
        private final Path profilePath;
        private GuiTextField nameField;

        private ProfileRename(GuiScreen parent, Path profilePath, String initialName) {
            super(parent);
            this.profilePath = profilePath;
        }

        @Override
        public void initGui() {
            Keyboard.enableRepeatEvents(true);
            buttonList.clear();
            int left = (width - FORM_WIDTH) / 2;
            nameField = LegacyVersionBridge.textField(100, font(), left, FIELD_Y, FORM_WIDTH, 20);
            nameField.setMaxStringLength(128);
            // 親画面から渡された名前を初期値とし、空の場合はそのままにする。
            String initial = "";
            try {
                ProfileManifest manifest = service().readManifest(profilePath);
                initial = manifest.name == null ? "" : manifest.name;
            } catch (Exception ignored) {
                // マニフェスト読み込み失敗時は空欄から始め、ユーザーが入力できるようにする。
            }
            nameField.setText(initial);
            buttonList.add(createButton(1, width / 2 - 104, height - 32, 100, BUTTON_HEIGHT,
                    tr("screen.universal_config.rename")));
            buttonList.add(createButton(0, width / 2 + 4, height - 32, 100, BUTTON_HEIGHT,
                    tr("screen.universal_config.back")));
        }

        private void rename() {
            String value = nameField.getText();
            if (value == null || value.trim().isEmpty()) {
                status = tr("screen.universal_config.profile_name_required");
                return;
            }
            try {
                service().renameProfile(profilePath, value);
                back();
            } catch (Exception ex) {
                fail("screen.universal_config.rename_failed", ex);
            }
        }

        @Override
        public void updateScreen() {
            nameField.updateCursorCounter();
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) back();
            if (button.id == 1) rename();
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) { back(); return; }
            nameField.textboxKeyTyped(typedChar, keyCode);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            try { super.mouseClicked(mouseX, mouseY, mouseButton); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
            nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.profile_rename_title"), width / 2, 18, TEXT);
            int left = (width - FORM_WIDTH) / 2;
            drawString(font(), tr("screen.universal_config.profile_rename_label"), left, 44, MUTED);
            nameField.drawTextBox();
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawStatus();
        }
    }

    private static final class ProfileCreate extends Base {
        private GuiTextField name;
        private GuiTextField description;
        private boolean keybinds = true;
        private boolean client = true;
        private boolean mods = false;
        private int iconIndex;
        private static final String[] ICONS = {ProfileIcon.GRASS_BLOCK, ProfileIcon.CRAFTING_TABLE, ProfileIcon.BOOKSHELF,
                ProfileIcon.COBBLESTONE, ProfileIcon.TNT, ProfileIcon.CHEST, ProfileIcon.FURNACE, ProfileIcon.DIAMOND_BLOCK};

        private ProfileCreate(GuiScreen parent) {
            super(parent);
        }

        @Override
        public void initGui() {
            Keyboard.enableRepeatEvents(true);
            buttonList.clear();
            int left = width / 2 - 110;
            name = LegacyVersionBridge.textField(100, font(), left, 44, 220, 20);
            name.setMaxStringLength(96);
            description = LegacyVersionBridge.textField(101, font(), left, 78, 220, 20);
            description.setMaxStringLength(256);
            if (compactLayout()) {
                buttonList.add(createButton(1, left, 108, 106, BUTTON_HEIGHT, ""));
                buttonList.add(createButton(2, left + 114, 108, 106, BUTTON_HEIGHT, ""));
                buttonList.add(createButton(3, left, 134, 220, BUTTON_HEIGHT, ""));
            } else {
                // Full-width rows prevent translated labels from competing for space on the create screen.
                buttonList.add(createButton(1, left, 108, 220, BUTTON_HEIGHT, ""));
                buttonList.add(createButton(2, left, 134, 220, BUTTON_HEIGHT, ""));
                buttonList.add(createButton(3, left, 160, 220, BUTTON_HEIGHT, ""));
            }
            int iconY = iconButtonsY();
            for (int i = 0; i < ICONS.length; i++) {
                buttonList.add(createButton(20 + i, left + 3 + i * 27, iconY, 24, BUTTON_HEIGHT, ""));
            }
            buttonList.add(createButton(5, left, height - 32, 106, BUTTON_HEIGHT, tr("screen.universal_config.save")));
            buttonList.add(createButton(0, left + 114, height - 32, 106, BUTTON_HEIGHT, tr("screen.universal_config.back")));
            refreshLabels();
        }

        private int iconButtonsY() {
            return Math.min(compactLayout() ? 184 : 210, height - 68);
        }

        private boolean compactLayout() {
            return height < 270;
        }

        private void refreshLabels() {
            button(1).displayString = checkLabel("screen.universal_config.target_keybinds", keybinds);
            button(2).displayString = checkLabel("screen.universal_config.target_client", client);
            button(3).displayString = checkLabel("screen.universal_config.target_mods", mods);
        }

        private GuiButton button(int id) {
            for (Object value : buttonList) {
                GuiButton button = (GuiButton) value;
                if (button.id == id) return button;
            }
            return null;
        }

        private String checkLabel(String key, boolean value) {
            return (value ? "[x] " : "[ ] ") + tr(key);
        }

        @Override
        public void updateScreen() {
            name.updateCursorCounter();
            description.updateCursorCounter();
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) back();
            if (button.id == 1) keybinds = !keybinds;
            if (button.id == 2) client = !client;
            if (button.id == 3) mods = !mods;
            if (button.id >= 20 && button.id < 20 + ICONS.length) iconIndex = button.id - 20;
            if (button.id == 5) save();
            refreshLabels();
        }

        private void save() {
            try {
                ProfileCreateOptions options = new ProfileCreateOptions();
                options.name = name.getText();
                options.description = description.getText();
                options.icon = ICONS[iconIndex];
                options.includeKeybinds = keybinds;
                options.includeClientOptions = client;
                options.includeModConfigs = mods;
                service().createProfile(LegacyPlatform.gameDirectory(), options, LegacyPlatform.environment());
                back();
            } catch (Exception ex) {
                fail("screen.universal_config.create_failed", ex);
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == Keyboard.KEY_ESCAPE) { back(); return; }
            if (!name.textboxKeyTyped(typedChar, keyCode)) description.textboxKeyTyped(typedChar, keyCode);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            try { super.mouseClicked(mouseX, mouseY, mouseButton); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
            name.mouseClicked(mouseX, mouseY, mouseButton);
            description.mouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.profile_create_title"), width / 2, 16, TEXT);
            int left = width / 2 - 110;
            drawString(font(), tr("screen.universal_config.profile_name_label"), left, 32, MUTED);
            drawString(font(), tr("screen.universal_config.profile_description_label"), left, 66, MUTED);
            drawString(font(), tr("screen.universal_config.profile_icon_selected",
                    tr("screen.universal_config.profile_icon_" + ICONS[iconIndex])), left, iconButtonsY() - 12, MUTED);
            name.drawTextBox();
            description.drawTextBox();
            super.drawScreen(mouseX, mouseY, partialTicks);
            for (int i = 0; i < ICONS.length; i++) {
                GuiButton iconButton = button(20 + i);
                int buttonX = iconButtonPositionX(iconButton);
                int buttonY = iconButtonsY();
                if (i == iconIndex) {
                    // Keep the vanilla button texture visible; selection is a one-pixel outline, not an opaque tile.
                    drawRect(buttonX - 1, buttonY - 1, buttonX + 25, buttonY, 0xFFD8E3FF);
                    drawRect(buttonX - 1, buttonY + BUTTON_HEIGHT, buttonX + 25,
                            buttonY + BUTTON_HEIGHT + 1, 0xFFD8E3FF);
                    drawRect(buttonX - 1, buttonY, buttonX, buttonY + BUTTON_HEIGHT, 0xFFD8E3FF);
                    drawRect(buttonX + 24, buttonY, buttonX + 25, buttonY + BUTTON_HEIGHT, 0xFFD8E3FF);
                }
                LegacyVersionBridge.drawProfileIcon(mc, ICONS[i], buttonX + 4, buttonY + 2, 16);
            }
            drawStatus();
        }

        private int iconButtonPositionX(GuiButton iconButton) {
            // 1.12 renamed xPosition to x, so derive the stable position from the ID rather than exposing it in the bridge.
            return width / 2 - 107 + (iconButton.id - 20) * 27;
        }
    }

    private static final class ConfirmApply extends Base {
        private final Path profile;
        private ProfileDiff diff;
        private ProfileManifest manifest;

        private ConfirmApply(GuiScreen parent, Path profile) {
            super(parent);
            this.profile = profile;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            try {
                ProfileService service = service();
                diff = service.diff(LegacyPlatform.gameDirectory(), profile, LegacyPlatform.environment());
                manifest = service.readManifest(profile);
            } catch (Exception ex) {
                fail("screen.universal_config.confirm_load_failed", ex);
            }
            buttonList.add(createButton(1, width / 2 - 104, height - 32, 100, BUTTON_HEIGHT,
                    tr(diff != null && diff.riskLevel == RiskLevel.HIGH
                            ? "screen.universal_config.confirm_schedule_high" : "screen.universal_config.use_profile")));
            buttonList.add(createButton(0, width / 2 + 4, height - 32, 100, BUTTON_HEIGHT, tr("screen.universal_config.back")));
            ((GuiButton) buttonList.get(0)).enabled = diff != null;
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) back();
            if (button.id == 1) {
                try {
                    service().scheduleApplyOnNextStart(LegacyPlatform.gameDirectory(), profile, LegacyPlatform.environment());
                    mc.displayGuiScreen(new ApplyScheduled(parent));
                } catch (Exception ex) {
                    fail("screen.universal_config.schedule_failed", ex);
                }
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.profile_confirm_title"), width / 2, 14, TEXT);
            int left = contentLeft();
            int top = 36;
            drawRect(left, top, left + contentWidth(), height - 40, PANEL);
            if (manifest != null) {
                drawString(font(), manifest.name == null ? profile.getFileName().toString() : manifest.name, left + 8, top + 8, TEXT);
            }
            if (diff != null) {
                int y = top + 26;
                drawString(font(), tr("screen.universal_config.confirm_risk",
                        tr(diff.riskLevel == RiskLevel.HIGH ? "screen.universal_config.risk_high"
                                : diff.riskLevel == RiskLevel.MEDIUM ? "screen.universal_config.risk_medium"
                                : "screen.universal_config.risk_low")), left + 8, y, diff.riskLevel == RiskLevel.HIGH ? 0xFF7777 : MUTED);
                y += 14;
                y = drawItems(diff.warnings, left + 8, y, contentWidth() - 16, 0xFFB070);
                y = drawItems(diff.addedFiles, left + 8, y, contentWidth() - 16, MUTED);
                drawItems(diff.replacedFiles, left + 8, y, contentWidth() - 16, MUTED);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawStatus();
        }

        private int drawItems(List<String> items, int x, int y, int maxWidth, int color) {
            int bottom = height - 48;
            for (String item : items) {
                if (y + 10 > bottom) break;
                drawString(font(), font().trimStringToWidth("• " + item, maxWidth), x, y, color);
                y += 11;
            }
            return y;
        }
    }

    private static final class ApplyScheduled extends Base {
        private ApplyScheduled(GuiScreen parent) {
            super(parent);
        }

        @Override
        public void initGui() {
            buttonList.clear();
            buttonList.add(createButton(0, width / 2 - 104, height / 2 + 16, 100, BUTTON_HEIGHT, tr("screen.universal_config.apply_scheduled_later")));
            buttonList.add(createButton(1, width / 2 + 4, height / 2 + 16, 100, BUTTON_HEIGHT, tr("screen.universal_config.restart_now")));
            buttonList.add(createButton(2, width / 2 - 100, height / 2 + 44, 200, BUTTON_HEIGHT, tr("screen.universal_config.apply_scheduled_cancel")));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            try {
                if (button.id == 0) back();
                if (button.id == 2) { service().clearPendingImport(LegacyPlatform.gameDirectory()); back(); }
                if (button.id == 1) { LegacyPlatform.scheduleRestart(); LegacyVersionBridge.shutdown(mc); }
            } catch (Exception ex) {
                fail("screen.universal_config.restart_failed_title", ex);
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.apply_scheduled_title"), width / 2, height / 2 - 26, TEXT);
            drawCenteredString(font(), tr("screen.universal_config.apply_scheduled_line1"), width / 2, height / 2 - 8, MUTED);
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawStatus();
        }
    }

    private static final class Backups extends Base {
        private List<BackupSummary> backups = Collections.emptyList();
        private int selected = -1;
        private int scroll;

        private Backups(GuiScreen parent) {
            super(parent);
        }

        @Override
        public void initGui() {
            buttonList.clear();
            try {
                backups = service().listBackups();
                if (!backups.isEmpty()) selected = Math.min(Math.max(0, selected), backups.size() - 1);
            } catch (Exception ex) {
                fail("screen.universal_config.load_failed", ex);
            }
            buttonList.add(createButton(1, width / 2 - 104, height - 32, 100, BUTTON_HEIGHT, tr("screen.universal_config.restore")));
            buttonList.add(createButton(0, width / 2 + 4, height - 32, 100, BUTTON_HEIGHT, tr("screen.universal_config.back")));
            ((GuiButton) buttonList.get(0)).enabled = selected >= 0;
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) back();
            if (button.id == 1 && selected >= 0) {
                try {
                    service().restore(LegacyPlatform.gameDirectory(), backups.get(selected).path());
                    LegacyPlatform.reloadOptions();
                    status = tr("screen.universal_config.restore_complete");
                } catch (Exception ex) {
                    fail("screen.universal_config.restore_failed", ex);
                }
            }
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
            try { super.mouseClicked(mouseX, mouseY, mouseButton); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
            int left = contentLeft();
            if (mouseX >= left && mouseX <= left + contentWidth() && mouseY >= 36 && mouseY < height - 40) {
                int index = scroll + (mouseY - 36) / 22;
                if (index >= 0 && index < backups.size()) selected = index;
                ((GuiButton) buttonList.get(0)).enabled = selected >= 0;
            }
        }

        @Override
        public void handleMouseInput() {
            try { super.handleMouseInput(); } catch (Exception ex) { fail("screen.universal_config.action_failed", ex); }
            int wheel = Mouse.getEventDWheel();
            int visible = Math.max(1, (height - 76) / 22);
            if (wheel != 0) scroll = Math.max(0, Math.min(Math.max(0, backups.size() - visible), scroll + (wheel < 0 ? 1 : -1)));
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(font(), tr("screen.universal_config.backups"), width / 2, 14, TEXT);
            int left = contentLeft();
            int visible = Math.max(1, (height - 76) / 22);
            int end = Math.min(backups.size(), scroll + visible);
            for (int i = scroll; i < end; i++) {
                int y = 36 + (i - scroll) * 22;
                if (i == selected) drawRect(left, y, left + contentWidth(), y + 20, SELECTED);
                drawString(font(), font().trimStringToWidth(backups.get(i).path().getFileName().toString(), contentWidth() - 12), left + 6, y + 6, TEXT);
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            drawStatus();
        }
    }
}
