package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupListScreen extends Screen {
    private static final int SCREEN_MARGIN = 16;
    private static final int BUTTON_GAP = 8;
    private static final int BUTTON_Y = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_TEXT_X = 12;
    private static final int ROW_TEXT_GAP = 12;
    private static final int ROW_START_Y = 44;
    private static final int ROW_STEP = 36;

    private final Screen parent;
    private List<BackupSummary> backups = new ArrayList<>();
    private Text status = Text.empty();

    public BackupListScreen(Screen parent) {
        super(Text.translatable("screen.universal_config.backup_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
        rebuildButtons();
    }

    private void reload() {
        try {
            backups = ScreenUtil.service().listBackups();
            status = Text.empty();
        } catch (UniversalConfigException ex) {
            backups = List.of();
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        clearChildren();
        Text refreshText = Text.translatable("screen.universal_config.refresh");
        Text backText = Text.translatable("screen.universal_config.back");
        int backWidth = buttonWidth(backText, 64);
        int refreshWidth = buttonWidth(refreshText, 64);
        int right = width - SCREEN_MARGIN;
        addDrawableChild(ButtonWidget.builder(backText, button -> client.setScreen(parent))
                .dimensions(right - backWidth, BUTTON_Y, backWidth, BUTTON_HEIGHT).build());
        right -= backWidth + BUTTON_GAP;
        addDrawableChild(ButtonWidget.builder(refreshText, button -> {
            reload();
            rebuildButtons();
        }).dimensions(right - refreshWidth, BUTTON_Y, refreshWidth, BUTTON_HEIGHT).build());

        Text restoreText = Text.translatable("screen.universal_config.restore");
        int restoreWidth = buttonWidth(restoreText, 72);
        int restoreX = width - SCREEN_MARGIN - restoreWidth;
        int y = 42;
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            Path backupPath = backup.path();
            addDrawableChild(ButtonWidget.builder(restoreText, button -> confirmRestore(backupPath))
                    .dimensions(restoreX, y, restoreWidth, BUTTON_HEIGHT).build());
            y += ROW_STEP;
        }
    }

    private int buttonWidth(Text label, int minimumWidth) {
        return Math.min(140, Math.max(minimumWidth, textRenderer.getWidth(label) + 16));
    }

    private void confirmRestore(Path backupPath) {
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                restore(backupPath);
            } else {
                client.setScreen(this);
            }
        }, Text.translatable("screen.universal_config.confirm_restore_title"),
                Text.translatable("screen.universal_config.confirm_restore_message")));
    }

    private void restore(Path backupPath) {
        Text restoreStatus;
        try {
            ScreenUtil.service().restore(ScreenUtil.instancePath(), backupPath);
            ScreenUtil.reloadMinecraftOptionsFromDisk();
            restoreStatus = Text.translatable("screen.universal_config.backup_restored", backupPath.getFileName());
        } catch (UniversalConfigException ex) {
            restoreStatus = ScreenUtil.errorText(ex);
        }
        client.setScreen(this);
        status = restoreStatus;
        rebuildButtons();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ScreenUtil.renderBackground(this, context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, trimmed(status, width - ROW_TEXT_X * 2),
                ROW_TEXT_X, 28, 0xFFFFCC66);
        Text restoreText = Text.translatable("screen.universal_config.restore");
        int restoreWidth = buttonWidth(restoreText, 72);
        int restoreX = width - SCREEN_MARGIN - restoreWidth;
        int textWidth = Math.max(0, restoreX - ROW_TEXT_X - ROW_TEXT_GAP);
        int y = ROW_START_Y;
        if (backups.isEmpty()) {
            context.drawTextWithShadow(textRenderer,
                    trimmed(Text.translatable("screen.universal_config.backup_empty"), width - ROW_TEXT_X * 2),
                    ROW_TEXT_X, y, 0xFFDDDDDD);
        }
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            String created = backup.manifest() == null || backup.manifest().createdAt == null
                    ? Text.translatable("screen.universal_config.date_unknown").getString()
                    : backup.manifest().createdAt;
            Text backupName = Text.literal(created + "  " + backup.path().getFileName());
            context.drawTextWithShadow(textRenderer, trimmed(backupName, textWidth),
                    ROW_TEXT_X, y, 0xFFFFFFFF);
            if (backup.manifest() != null) {
                Text details = Text.translatable("screen.universal_config.backup_details",
                        backup.manifest().minecraftVersion, backup.manifest().loader,
                        backup.manifest().files == null ? 0 : backup.manifest().files.size());
                context.drawTextWithShadow(textRenderer, trimmed(details, textWidth),
                        ROW_TEXT_X, y + 12, 0xFFBBBBBB);
            }
            y += ROW_STEP;
        }
        ScreenUtil.renderWidgets(this, context, mouseX, mouseY, delta);
    }

    private String trimmed(Text text, int maxWidth) {
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth)).getString();
    }
}
