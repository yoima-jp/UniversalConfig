package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.ProfileSummary;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.PendingImport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileListScreen extends Screen {
    private static final int LEFT_MARGIN = 12;
    private static final int TOP_BUTTON_Y = 8;
    private static final int STATUS_Y = 36;
    private static final int LIST_START_Y = 66;
    private static final int ROW_HEIGHT = 92;
    private static final int ROW_BUTTON_Y_OFFSET = 48;

    private final Screen parent;
    private List<ProfileSummary> profiles = new ArrayList<>();
    private PendingImport pendingImport;
    private Text status = Text.empty();

    public ProfileListScreen(Screen parent) {
        super(Text.literal("Universal Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reload();
        rebuildButtons();
    }

    private void reload() {
        try {
            profiles = ScreenUtil.service().listProfiles();
            pendingImport = ScreenUtil.service().readPendingImport(ScreenUtil.instancePath());
            status = pendingImport == null
                    ? Text.literal("共通フォルダ: " + ScreenUtil.service().settings().rootDirectory())
                    : Text.literal("次回起動で読み込み予約済み: " + pendingImport.profilePath);
        } catch (UniversalConfigException ex) {
            profiles = List.of();
            pendingImport = null;
            status = ScreenUtil.errorText(ex);
        }
    }

    private void rebuildButtons() {
        clearChildren();
        int y = LIST_START_Y;
        addDrawableChild(ButtonWidget.builder(Text.literal("出力先"), button -> openOutputDirectory())
                .dimensions(width - 304, TOP_BUTTON_Y, 64, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("作成"), button -> client.setScreen(new ProfileCreateScreen(this)))
                .dimensions(width - 234, TOP_BUTTON_Y, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("復元"), button -> client.setScreen(new BackupListScreen(this)))
                .dimensions(width - 176, TOP_BUTTON_Y, 52, 20).build());
        if (pendingImport != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("予約解除"), button -> clearPendingImport())
                    .dimensions(width - 386, TOP_BUTTON_Y, 76, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("更新"), button -> {
            reload();
            rebuildButtons();
        }).dimensions(width - 118, TOP_BUTTON_Y, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("閉じる"), button -> client.setScreen(parent))
                .dimensions(width - 60, TOP_BUTTON_Y, 52, 20).build());

        for (ProfileSummary summary : profiles) {
            if (y + ROW_BUTTON_Y_OFFSET > height - 28) {
                break;
            }
            Path profilePath = summary.path();
            addDrawableChild(ButtonWidget.builder(Text.literal("確認"), button -> client.setScreen(new ProfileConfirmScreen(this, profilePath)))
                    .dimensions(LEFT_MARGIN, y + ROW_BUTTON_Y_OFFSET, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("複製"), button -> duplicate(profilePath))
                    .dimensions(LEFT_MARGIN + 78, y + ROW_BUTTON_Y_OFFSET, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("削除"), button -> delete(profilePath))
                    .dimensions(LEFT_MARGIN + 156, y + ROW_BUTTON_Y_OFFSET, 70, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Export"), button -> export(profilePath))
                    .dimensions(LEFT_MARGIN + 234, y + ROW_BUTTON_Y_OFFSET, 82, 20).build());
            y += ROW_HEIGHT;
        }
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

    private void delete(Path path) {
        try {
            ScreenUtil.service().deleteProfile(path);
            reload();
            rebuildButtons();
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void export(Path path) {
        try {
            Path exported = ScreenUtil.service().exportProfile(path, UniversalConfigPaths.exportDirectory(ScreenUtil.instancePath()));
            status = Text.literal("エクスポート: " + exported);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void clearPendingImport() {
        try {
            ScreenUtil.service().clearPendingImport(ScreenUtil.instancePath());
            reload();
            rebuildButtons();
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    private void openOutputDirectory() {
        try {
            Path directory = ScreenUtil.service().settings().rootDirectory().toAbsolutePath().normalize();
            Util.getOperatingSystem().open(directory.toUri());
            FileOperationLogger.info("OPEN_OUTPUT_DIRECTORY", directory, "opened by user");
            status = Text.literal("出力先を開きました: " + directory);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        drawTrimmed(context, status.getString(), LEFT_MARGIN, STATUS_Y, width - LEFT_MARGIN * 2, 0xBBBBBB);
        int y = LIST_START_Y;
        if (profiles.isEmpty()) {
            drawTrimmed(context, "プロファイルがありません。作成ボタンから現在の構成を保存できます。", LEFT_MARGIN, LIST_START_Y, width - LEFT_MARGIN * 2, 0xDDDDDD);
            if (pendingImport != null) {
                drawTrimmed(context, "予約を反映するには Minecraft を再起動してください。", LEFT_MARGIN, LIST_START_Y + 12, width - LEFT_MARGIN * 2, 0xFFCC66);
            }
        }
        for (ProfileSummary summary : profiles) {
            if (y + ROW_BUTTON_Y_OFFSET > height - 28) {
                break;
            }
            String source = summary.manifest().source == null
                    ? "unknown"
                    : summary.manifest().source.minecraftVersion + " / " + summary.manifest().source.loader;
            drawTrimmed(context, summary.manifest().name, LEFT_MARGIN, y, width - LEFT_MARGIN * 2, 0xFFFFFF);
            drawTrimmed(context, source + "  updated: " + summary.manifest().updatedAt, LEFT_MARGIN, y + 12, width - LEFT_MARGIN * 2, 0xBBBBBB);
            drawTrimmed(context, summary.manifest().description, LEFT_MARGIN, y + 24, width - LEFT_MARGIN * 2, 0x999999);
            y += ROW_HEIGHT;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawTrimmed(DrawContext context, String text, int x, int y, int maxWidth, int color) {
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text == null ? "" : text, Math.max(0, maxWidth)), x, y, color);
    }
}
