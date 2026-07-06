package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileDiff;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileConfirmScreen extends Screen {
    private final Screen parent;
    private final Path profilePath;
    private ProfileManifest manifest;
    private ProfileDiff diff;
    private List<String> lines = List.of();
    private Text status = Text.empty();
    private int scroll;

    public ProfileConfirmScreen(Screen parent, Path profilePath) {
        super(Text.literal("読み込み確認"));
        this.parent = parent;
        this.profilePath = profilePath;
    }

    @Override
    protected void init() {
        loadDiff();
        addDrawableChild(ButtonWidget.builder(Text.literal(scheduleButtonLabel()), button -> schedule())
                .dimensions(width / 2 - 160, height - 30, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("差分を再読み込み"), button -> loadDiff())
                .dimensions(width / 2 - 4, height - 30, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("戻る"), button -> client.setScreen(parent))
                .dimensions(width / 2 + 122, height - 30, 70, 20).build());
    }

    private void loadDiff() {
        try {
            ProfileService service = ScreenUtil.service();
            manifest = service.readManifest(profilePath);
            diff = service.diff(ScreenUtil.instancePath(), profilePath, ScreenUtil.environment());
            lines = buildLines();
        } catch (UniversalConfigException ex) {
            diff = new ProfileDiff();
            lines = List.of();
            status = ScreenUtil.errorText(ex);
        }
    }

    private List<String> buildLines() {
        List<String> result = new ArrayList<>();
        result.add("プロファイル: " + manifest.name);
        result.add("説明: " + manifest.description);
        result.add("作成元: Minecraft " + manifest.source.minecraftVersion + " / " + manifest.source.loader
                + " " + manifest.source.loaderVersion);
        result.add("現在: Minecraft " + ScreenUtil.environment().minecraftVersion() + " / "
                + ScreenUtil.environment().loaderId() + " " + ScreenUtil.environment().loaderVersion());
        result.add("対応想定: " + manifest.compatibility.minecraftVersionRange + " / " + manifest.compatibility.mode);
        result.add("テスト済み: " + manifest.compatibility.testedVersions);
        result.add("リスク: " + diff.riskLevel);
        result.add("適用方式: 次回起動時に読み込みます");
        result.add("安全対策: 次回起動時の適用前に .ucbackup を必ず作成します");
        result.add("操作後: Minecraftを終了し、もう一度起動してください");
        result.add("");
        ScreenUtil.appendSection(result, "警告:", diff.warnings);
        ScreenUtil.appendSection(result, "Checksum:", diff.checksumWarnings);
        ScreenUtil.appendSection(result, "変更されるキー設定:", diff.changedKeybinds);
        ScreenUtil.appendSection(result, "追加されるファイル:", diff.addedFiles);
        ScreenUtil.appendSection(result, "置き換えられるファイル:", diff.replacedFiles);
        ScreenUtil.appendSection(result, "スキップ:", diff.skippedItems);
        return result;
    }

    private String scheduleButtonLabel() {
        return switch (diff.riskLevel) {
            case HIGH -> "危険を理解して予約";
            case MEDIUM -> "バックアップ読み込み予約";
            case LOW -> "次回起動で読み込む";
        };
    }

    private void schedule() {
        try {
            Path pendingPath = ScreenUtil.service().scheduleApplyOnNextStart(ScreenUtil.instancePath(), profilePath, ScreenUtil.environment());
            status = Text.literal("次回起動で読み込みます。予約: " + pendingPath);
            loadDiff();
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, lines.size() - Math.max(1, (height - 78) / 11));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(amount) * 3));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, status, 12, 26, 0xFFCC66);
        int y = 44;
        int visible = Math.max(1, (height - 82) / 11);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            int color = lines.get(i).startsWith("-") ? 0xDDDDDD : 0xFFFFFF;
            if (lines.get(i).contains("警告") || lines.get(i).contains("HIGH")) {
                color = 0xFF8888;
            }
            context.drawTextWithShadow(textRenderer, lines.get(i), 12, y, color);
            y += 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
