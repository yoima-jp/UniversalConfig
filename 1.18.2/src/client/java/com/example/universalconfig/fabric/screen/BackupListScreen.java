package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupListScreen extends Screen {
    private final Screen parent;
    private List<BackupSummary> backups = new ArrayList<>();
    private Text status = ScreenUtil.empty();

    public BackupListScreen(Screen parent) {
        super(ScreenUtil.translatable("screen.universal_config.backup_title"));
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
            status = ScreenUtil.empty();
        } catch (UniversalConfigException ex) {
            backups = List.of();
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        clearChildren();
        addDrawableChild(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.refresh"), button -> {
            reload();
            rebuildButtons();
        }).dimensions(width - 118, 8, 52, 20).build());
        addDrawableChild(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.back"), button -> client.setScreen(parent))
                .dimensions(width - 60, 8, 52, 20).build());

        int y = 42;
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            Path backupPath = backup.path();
            addDrawableChild(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.restore"), button -> confirmRestore(backupPath))
                    .dimensions(width - 80, y, 60, 20).build());
            y += 36;
        }
    }

    private void confirmRestore(Path backupPath) {
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) restore(backupPath);
            else client.setScreen(this);
        }, ScreenUtil.translatable("screen.universal_config.confirm_restore_title"),
                ScreenUtil.translatable("screen.universal_config.confirm_restore_message")));
    }

    private void restore(Path backupPath) {
        try {
            ScreenUtil.service().restore(ScreenUtil.instancePath(), backupPath);
            ScreenUtil.reloadMinecraftOptionsFromDisk();
            status = ScreenUtil.translatable("screen.universal_config.backup_restored", backupPath.getFileName());
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        ScreenUtil.drawCenteredTextWithShadow(context, textRenderer, title, width / 2, 14, 0xFFFFFF);
        ScreenUtil.drawTextWithShadow(context, textRenderer, status, 12, 28, 0xFFCC66);
        int y = 44;
        if (backups.isEmpty()) {
            ScreenUtil.drawTextWithShadow(context, textRenderer, ScreenUtil.translatable("screen.universal_config.backup_empty"), 12, y, 0xDDDDDD);
        }
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            if (backup == null || backup.path() == null) continue;
            String created = backup.manifest() == null || backup.manifest().createdAt == null ? "unknown" : backup.manifest().createdAt;
            ScreenUtil.drawTextWithShadow(context, textRenderer, created + "  " + backup.path().getFileName(), 12, y, 0xFFFFFF);
            if (backup.manifest() != null) {
                ScreenUtil.drawTextWithShadow(context, textRenderer, ScreenUtil.translatable("screen.universal_config.backup_details",
                        backup.manifest().minecraftVersion, backup.manifest().loader,
                        backup.manifest().files == null ? 0 : backup.manifest().files.size()), 12, y + 12, 0xBBBBBB);
            }
            y += 36;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
