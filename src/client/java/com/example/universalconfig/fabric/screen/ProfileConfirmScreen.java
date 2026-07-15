package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileDiff;
import com.example.universalconfig.core.ProfileManifest;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.RiskLevel;
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
    private List<DisplayLine> lines = List.of();
    private Text status = Text.empty();
    private int scroll;

    public ProfileConfirmScreen(Screen parent, Path profilePath) {
        super(Text.translatable("screen.universal_config.profile_confirm_title"));
        this.parent = parent;
        this.profilePath = profilePath;
    }

    @Override
    protected void init() {
        loadDiff();
        int buttonLeft = width / 2 - 114;
        addDrawableChild(ButtonWidget.builder(scheduleButtonLabel(), button -> schedule())
                .dimensions(buttonLeft, height - 30, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.back"), button -> client.setScreen(parent))
                .dimensions(buttonLeft + 158, height - 30, 70, 20).build());
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

    private List<DisplayLine> buildLines() {
        List<DisplayLine> result = new ArrayList<>();
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_profile", manifest.name)));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_description", manifest.description)));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_source", manifest.source.minecraftVersion,
                manifest.source.loader, manifest.source.loaderVersion)));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_current", ScreenUtil.environment().minecraftVersion(),
                ScreenUtil.environment().loaderId(), ScreenUtil.environment().loaderVersion())));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_compatibility",
                manifest.compatibility.minecraftVersionRange, manifest.compatibility.mode)));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_tested", manifest.compatibility.testedVersions)));
        result.add(new DisplayLine(Text.translatable("screen.universal_config.confirm_risk",
                Text.translatable(riskLevelKey(diff.riskLevel))), false, diff.riskLevel == RiskLevel.HIGH));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_apply_mode")));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_safety")));
        result.add(DisplayLine.normal(Text.translatable("screen.universal_config.confirm_after_action")));
        result.add(DisplayLine.normal(Text.empty()));
        appendSection(result, "screen.universal_config.confirm_warnings", diff.warnings, true);
        appendSection(result, "screen.universal_config.confirm_checksums", diff.checksumWarnings, true);
        appendSection(result, "screen.universal_config.confirm_changed_keybinds", diff.changedKeybinds, false);
        appendSection(result, "screen.universal_config.confirm_added_files", diff.addedFiles, false);
        appendSection(result, "screen.universal_config.confirm_replaced_files", diff.replacedFiles, false);
        appendSection(result, "screen.universal_config.confirm_skipped", diff.skippedItems, false);
        return result;
    }

    private Text scheduleButtonLabel() {
        return switch (diff.riskLevel) {
            case HIGH -> Text.translatable("screen.universal_config.confirm_schedule_high");
            case MEDIUM -> Text.translatable("screen.universal_config.confirm_schedule_medium");
            case LOW -> Text.translatable("screen.universal_config.confirm_schedule_low");
        };
    }

    private String riskLevelKey(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case HIGH -> "screen.universal_config.risk_high";
            case MEDIUM -> "screen.universal_config.risk_medium";
            case LOW -> "screen.universal_config.risk_low";
        };
    }

    private void appendSection(List<DisplayLine> result, String titleKey, List<String> values, boolean warning) {
        if (values.isEmpty()) {
            return;
        }
        result.add(new DisplayLine(Text.translatable(titleKey), false, warning));
        for (String value : values) {
            result.add(new DisplayLine(Text.translatable("screen.universal_config.confirm_list_item", value), true, warning));
        }
    }

    private void schedule() {
        try {
            ScreenUtil.service().scheduleApplyOnNextStart(ScreenUtil.instancePath(), profilePath, ScreenUtil.environment());
            loadDiff();
            client.setScreen(new ApplyScheduledScreen(parent));
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
            DisplayLine line = lines.get(i);
            int color = line.warning() ? 0xFF8888 : line.listItem() ? 0xDDDDDD : 0xFFFFFF;
            context.drawTextWithShadow(textRenderer, line.text(), 12, y, color);
            y += 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    // 色を翻訳後の文字列から推測すると、ユーザー入力やファイル名で誤判定する。
    // 表示上の役割を保持し、言語や動的な値に依存せず描画する。
    private record DisplayLine(Text text, boolean listItem, boolean warning) {
        private static DisplayLine normal(Text text) {
            return new DisplayLine(text, false, false);
        }
    }
}
