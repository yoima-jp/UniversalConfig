package com.example.universalconfig.forge.screen;

import net.minecraft.client.gui.components.Button;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupListScreen extends Screen {
    private final Screen parent;
    private List<BackupSummary> backups = new ArrayList<>();
    private Component status = Component.empty();

    public BackupListScreen(Screen parent) {
        super(Component.translatable("screen.universal_config.backup_title"));
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
            status = Component.empty();
        } catch (UniversalConfigException ex) {
            backups = List.of();
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.refresh"), button -> {
            reload();
            rebuildButtons();
        }).bounds(width - 118, 8, 52, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.back"), button -> minecraft.setScreen(parent))
                .bounds(width - 60, 8, 52, 20).build());

        int y = 42;
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            Path backupPath = backup.path();
            addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.restore"), button -> confirmRestore(backupPath))
                    .bounds(width - 80, y, 60, 20).build());
            y += 36;
        }
    }

    private void confirmRestore(Path backupPath) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) restore(backupPath);
            else minecraft.setScreen(this);
        }, Component.translatable("screen.universal_config.confirm_restore_title"),
                Component.translatable("screen.universal_config.confirm_restore_message")));
    }

    private void restore(Path backupPath) {
        Component restoreStatus;
        try {
            ScreenUtil.service().restore(ScreenUtil.instancePath(), backupPath);
            ScreenUtil.reloadMinecraftOptionsFromDisk();
            restoreStatus = Component.translatable("screen.universal_config.backup_restored", backupPath.getFileName());
        } catch (UniversalConfigException ex) {
            restoreStatus = ScreenUtil.errorText(ex);
        }
        // Returning from confirmation initializes the list and clears its status; restore the result afterwards.
        minecraft.setScreen(this);
        status = restoreStatus;
        rebuildButtons();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(PoseStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawCenteredString(context, font, title, width / 2, 14, 0xFFFFFF);
        drawString(context, font, status, 12, 28, 0xFFCC66);
        int y = 44;
        if (backups.isEmpty()) {
            drawString(context, font, Component.translatable("screen.universal_config.backup_empty"), 12, y, 0xDDDDDD);
        }
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            String created = backup.manifest() == null || backup.manifest().createdAt == null
                    ? Component.translatable("screen.universal_config.date_unknown").getString() : backup.manifest().createdAt;
            drawString(context, font, created + "  " + backup.path().getFileName(), 12, y, 0xFFFFFF);
            if (backup.manifest() != null) {
                drawString(context, font, Component.translatable("screen.universal_config.backup_details", backup.manifest().minecraftVersion,
                        backup.manifest().loader, backup.manifest().files == null ? 0 : backup.manifest().files.size()), 12, y + 12, 0xBBBBBB);
            }
            y += 36;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
