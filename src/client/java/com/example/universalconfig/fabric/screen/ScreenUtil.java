package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileDiff;
import com.example.universalconfig.core.RiskLevel;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.fabric.FabricEnvironmentDetector;
import com.example.universalconfig.fabric.UniversalConfigMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;

final class ScreenUtil {
    private ScreenUtil() {
    }

    static ProfileService service() throws UniversalConfigException {
        MinecraftClient client = UniversalConfigMod.client();
        UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(client.runDirectory.toPath());
        return new ProfileService(settings);
    }

    static Path instancePath() {
        return UniversalConfigMod.client().runDirectory.toPath();
    }

    static com.example.universalconfig.core.MinecraftEnvironment environment() {
        return FabricEnvironmentDetector.detect(instancePath());
    }

    static Text literal(String value) {
        return Text.literal(value == null ? "" : value);
    }

    static Text errorText(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Text.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static String applyButtonLabel(ProfileDiff diff) {
        if (diff.riskLevel == RiskLevel.HIGH) {
            return "危険を理解して読み込む";
        }
        if (diff.riskLevel == RiskLevel.MEDIUM) {
            return "バックアップして読み込む";
        }
        return "読み込む";
    }

    static void appendSection(List<String> lines, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        lines.add(title);
        for (String value : values) {
            lines.add("- " + value);
        }
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftClient client = UniversalConfigMod.client();
            client.options.load();
            KeyBinding.updateKeysByCode();
            client.options.write();
            FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", instancePath().resolve("options.txt"), "load/updateKeysByCode/write");
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", instancePath().resolve("options.txt"), "failed", ex);
            throw new UniversalConfigException("Minecraftの設定再読み込みに失敗しました。再起動前に設定が戻る可能性があります。", ex);
        }
    }
}
