package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.BackupSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BackupListScreen extends Screen {
    private final Screen parent;
    private List<BackupSummary> backups = new ArrayList<>();
    private Component status = TextComponent.EMPTY;

    public BackupListScreen(Screen parent) {
        super(new TranslatableComponent("screen.universal_config.backups"));
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
            status = TextComponent.EMPTY;
        } catch (UniversalConfigException ex) {
            backups = List.of();
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        clearWidgets();
        addRenderableWidget(Button.builder(new TranslatableComponent("screen.universal_config.refresh"), button -> {
            reload();
            rebuildButtons();
        }).bounds(width - 118, 8, 52, 20).build());
        addRenderableWidget(Button.builder(new TranslatableComponent("screen.universal_config.back"), button -> minecraft.setScreen(parent))
                .bounds(width - 60, 8, 52, 20).build());

        int y = 42;
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            Path backupPath = backup.path();
            addRenderableWidget(Button.builder(new TranslatableComponent("screen.universal_config.restore"), button -> restore(backupPath))
                    .bounds(width - 80, y, 60, 20).build());
            y += 36;
        }
    }

    private void restore(Path backupPath) {
        try {
            ScreenUtil.service().restore(ScreenUtil.instancePath(), backupPath);
            ScreenUtil.reloadMinecraftOptionsFromDisk();
            status = new TranslatableComponent("screen.universal_config.restore_complete");
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(PoseStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawCenteredString(context, font, title, width / 2, 14, 0xFFFFFF);
        drawString(context, font, status, 12, 28, 0xFFCC66);
        int y = 44;
        if (backups.isEmpty()) {
            drawString(context, font, "バックアップがありません。プロファイル適用時に自動作成されます。", 12, y, 0xDDDDDD);
        }
        for (BackupSummary backup : backups) {
            if (y > height - 28) {
                break;
            }
            String created = backup.manifest().createdAt == null ? "unknown" : backup.manifest().createdAt;
            drawString(context, font, created + "  " + backup.path().getFileName(), 12, y, 0xFFFFFF);
            drawString(context, font, "対象: " + backup.manifest().minecraftVersion + " / "
                    + backup.manifest().loader + "  files: " + backup.manifest().files.size(), 12, y + 12, 0xBBBBBB);
            y += 36;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
