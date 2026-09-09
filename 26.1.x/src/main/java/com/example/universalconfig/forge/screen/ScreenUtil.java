package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.forge.ForgeEnvironmentDetector;
import com.example.universalconfig.forge.MinecraftOptionsReloader;
import com.example.universalconfig.forge.UniversalConfigMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import java.nio.file.Path;

final class ScreenUtil {
    private ScreenUtil() {
    }

    static ProfileService service() throws UniversalConfigException {
        Minecraft minecraft = UniversalConfigMod.client();
        UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(minecraft.gameDirectory.toPath());
        return new ProfileService(settings);
    }

    static Path instancePath() {
        return UniversalConfigMod.client().gameDirectory.toPath();
    }

    static com.example.universalconfig.core.MinecraftEnvironment environment() {
        return ForgeEnvironmentDetector.detect(instancePath());
    }

    static Component literal(String value) {
        return Component.literal(value == null ? "" : value);
    }

    static Component errorText(Exception ex) {
        if (ex instanceof UniversalConfigException) {
            UniversalConfigException error = (UniversalConfigException) ex;
            if (error.translationKey() != null) {
                Object[] args = error.translationArgs();
                return args == null || args.length == 0
                        ? Component.translatable(error.translationKey())
                        : Component.translatable(error.translationKey(), args);
            }
        }
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Component.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static void drawProfileIcon(GuiGraphicsExtractor context, int x, int y, int size, String iconId) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier modelId = Identifier.withDefaultNamespace(ProfileIcon.normalize(iconId));
        TrackingItemStackRenderState renderState = new TrackingItemStackRenderState();
        renderState.displayContext = ItemDisplayContext.GUI;

        // 26.1.2ではタイトル画面の時点でItemStackの既定コンポーネントが未バインドのため、
        // new ItemStack(block) はクラッシュする。一方、クライアントリソースの3Dアイテムモデルは
        // 既にロード済みなので、モデルを直接RenderStateへ展開して旧版と同じ3D表示を維持する。
        minecraft.getModelManager().getItemModel(modelId).update(
                renderState,
                ItemStack.EMPTY,
                minecraft.getItemModelResolver(),
                ItemDisplayContext.GUI,
                null,
                null,
                0);

        if (size <= 16) {
            // 選択画面などのネイティブ16px表示はアトラス拡大が発生しないため通常経路を使う。
            context.guiRenderState.addItem(new GuiItemRenderState(
                    new Matrix3x2f(context.pose()), renderState, x, y, context.scissorStack.peek()));
            return;
        }

        // 26.1.2のGUIアイテムアトラスは16px固定。プロフィール一覧の28pxアイコンは、
        // size*guiScaleのPiPテクスチャへモデルを直接描画してから等倍blitする。
        context.guiRenderState.addPicturesInPictureState(new ProfileIconRenderState(
                modelId, renderState, x, y, x + size, y + size, context.scissorStack.peek()));
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftOptionsReloader.reloadFromDisk(UniversalConfigPaths.optionsFile(instancePath()));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException("Failed to reload Minecraft options; settings may revert before restart.",
                    "message.universal_config.reload_options_failed", ex);
        }
    }
    // Match Fabric's 26.x render-state extraction: explicit opacity prevents the previous screen showing through.
    static void renderOpaqueBackground(GuiGraphicsExtractor context) {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF101010);
    }

}
