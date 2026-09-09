package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupListScreen extends Screen {
    private final Screen parent;
    private List<BackupSummary> backups = new ArrayList<>();
    private ITextComponent status = StringTextComponent.EMPTY;

    public BackupListScreen(Screen parent) {
        super(new TranslationTextComponent("screen.universal_config.backup_title"));
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
            status = StringTextComponent.EMPTY;
        } catch (UniversalConfigException ex) {
            backups = java.util.Collections.emptyList();
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        buttons.clear();
        children.clear();
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.refresh"), button -> {
            reload();
            rebuildButtons();
        }).bounds(width - 118, 8, 52, 20).build());
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.back"), button -> minecraft.setScreen(parent))
                .bounds(width - 60, 8, 52, 20).build());

        int y = 42;
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            Path backupPath = backup.path();
            addButton(Button.builder(new TranslationTextComponent("screen.universal_config.restore"), button -> confirmRestore(backupPath))
                    .bounds(width - 80, y, 60, 20).build());
            y += 36;
        }
    }

    private void confirmRestore(Path backupPath) {
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) restore(backupPath);
            else minecraft.setScreen(this);
        }, new TranslationTextComponent("screen.universal_config.confirm_restore_title"),
                new TranslationTextComponent("screen.universal_config.confirm_restore_message")));
    }

    private void restore(Path backupPath) {
        ITextComponent restoreStatus;
        try {
            ScreenUtil.service().restore(ScreenUtil.instancePath(), backupPath);
            ScreenUtil.reloadMinecraftOptionsFromDisk();
            restoreStatus = new TranslationTextComponent("screen.universal_config.backup_restored", backupPath.getFileName());
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
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawCenteredString(context, font, title, width / 2, 14, 0xFFFFFF);
        drawString(context, font, status, 12, 28, 0xFFCC66);
        int y = 44;
        if (backups.isEmpty()) {
            drawString(context, font, new TranslationTextComponent("screen.universal_config.backup_empty"), 12, y, 0xDDDDDD);
        }
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            String created = backup.manifest() == null || backup.manifest().createdAt == null
                    ? new TranslationTextComponent("screen.universal_config.date_unknown").getString() : backup.manifest().createdAt;
            drawString(context, font, created + "  " + backup.path().getFileName(), 12, y, 0xFFFFFF);
            if (backup.manifest() != null) {
                drawString(context, font, new TranslationTextComponent("screen.universal_config.backup_details", backup.manifest().minecraftVersion,
                        backup.manifest().loader, backup.manifest().files == null ? 0 : backup.manifest().files.size()), 12, y + 12, 0xBBBBBB);
            }
            y += 36;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
