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
        return Component.translatable("screen.universal_config.load_failed");
    }

    static void drawProfileIcon(GuiGraphicsExtractor context, int x, int y, int size, String iconId) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier modelId = Identifier.withDefaultNamespace(ProfileIcon.normalize(iconId));
        TrackingItemStackRenderState renderState = new TrackingItemStackRenderState();
        renderState.displayContext = ItemDisplayContext.GUI;

        // The title screen has no world registry access, so ItemStack default components may still be unbound.
        // Client models are loaded already and can be expanded directly without constructing an ItemStack.
        minecraft.getModelManager().getItemModel(modelId).update(renderState, ItemStack.EMPTY,
                minecraft.getItemModelResolver(), ItemDisplayContext.GUI, null, null, 0);

        if (size <= 16) {
            context.guiRenderState.addItem(new GuiItemRenderState(
                    new Matrix3x2f(context.pose()), renderState, x, y, context.scissorStack.peek()));
            return;
        }

        // Render 28px profile icons into a target-sized PiP texture. Scaling the fixed 16px GUI item atlas
        // produces uneven nearest-neighbor pixels, which is why the normal item path looked coarse.
        context.guiRenderState.addPicturesInPictureState(new ProfileIconRenderState(
                modelId, renderState, x, y, x + size, y + size, context.scissorStack.peek()));
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            MinecraftOptionsReloader.reloadFromDisk(UniversalConfigPaths.optionsFile(instancePath()));
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException("Minecraftの設定再読み込みに失敗しました。再起動前に設定が戻る可能性があります。", ex);
        }
    }
}
