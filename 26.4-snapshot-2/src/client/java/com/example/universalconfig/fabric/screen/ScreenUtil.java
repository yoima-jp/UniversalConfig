package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.fabric.FabricEnvironmentDetector;
import com.example.universalconfig.fabric.UniversalConfigMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.example.universalconfig.core.ProfileIcon;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.example.universalconfig.fabric.mixin.ItemStackAccessor;

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
        return FabricEnvironmentDetector.detect(instancePath());
    }

    static Component literal(String value) {
        return Component.literal(value == null ? "" : value);
    }

    static Component errorText(Exception ex) {
        if (ex instanceof UniversalConfigException ucex && ucex.translationKey() != null) {
            Object[] args = ucex.translationArgs();
            return args == null || args.length == 0
                    ? Component.translatable(ucex.translationKey())
                    : Component.translatable(ucex.translationKey(), args);
        }

        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return Component.literal(current.getMessage() == null ? ex.toString() : current.getMessage());
    }

    static void setScreen(Minecraft minecraft, Screen screen) {
        UniversalConfigMod.scheduleScreen(minecraft, screen);
    }

    /**
     * Draw profile icons through Minecraft's 26.3 item-model extraction path.
     * Title screens are rendered before the client binds the item component
     * prototypes. In that state the public ItemStack constructors intentionally
     * reject the registry holder, so use a patched component map containing the
     * vanilla common defaults and this item's model id for a read-only GUI
     * preview. The resulting stack still goes through the normal item model
     * resolver and GuiGraphicsExtractor item render state. Use the regular
     * item submission method so the same owner/context-sensitive path as
     * vanilla inventory widgets is used when a client player is available.
     */
    static void drawIcon(GuiGraphicsExtractor context, String iconId, int x, int y, int size) {
        String itemId = switch (ProfileIcon.normalize(iconId)) {
            case ProfileIcon.CRAFTING_TABLE -> "crafting_table";
            case ProfileIcon.BOOKSHELF -> "bookshelf";
            case ProfileIcon.COBBLESTONE -> "cobblestone";
            case ProfileIcon.TNT -> "tnt";
            case ProfileIcon.CHEST -> "chest";
            case ProfileIcon.FURNACE -> "furnace";
            case ProfileIcon.DIAMOND_BLOCK -> "diamond_block";
            default -> "grass_block";
        };
        Holder.Reference<Item> item = BuiltInRegistries.ITEM
                .get(Identifier.withDefaultNamespace(itemId))
                .orElse(null);
        if (item == null) {
            return;
        }
        ItemStack stack;
        if (item.areComponentsBound()) {
            stack = new ItemStack(item);
        } else {
            PatchedDataComponentMap previewComponents =
                    new PatchedDataComponentMap(DataComponents.COMMON_ITEM_COMPONENTS);
            previewComponents.set(DataComponents.ITEM_MODEL,
                    Identifier.withDefaultNamespace(itemId));
            stack = ItemStackAccessor.universalConfig$create(item, 1, previewComponents);
        }
        if (size == 16) {
            context.item(stack, x, y);
            return;
        }
        float scale = size / 16.0F;
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        context.pose().scale(scale, scale);
        context.item(stack, 0, 0);
        context.pose().popMatrix();
    }

    static void reloadMinecraftOptionsFromDisk() throws UniversalConfigException {
        try {
            Minecraft minecraft = UniversalConfigMod.client();
            minecraft.options.load();
            KeyMapping.resetMapping();
            minecraft.options.save();
            FileOperationLogger.info("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "load/resetMapping/write");
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", UniversalConfigPaths.optionsFile(instancePath()), "failed", ex);
            throw new UniversalConfigException(
                    "Failed to reload Minecraft options; settings may revert before restart.",
                    "message.universal_config.reload_options_failed",
                    ex);
        }
    }

    /**
     * 26.3 uses render-state extraction instead of drawing directly into the
     * current framebuffer.  Fill the complete GUI surface with an opaque color
     * so a previous screen or frame can never show through this screen.
     */
    static void renderOpaqueBackground(net.minecraft.client.gui.GuiGraphicsExtractor context) {
        context.fill(0, 0, context.guiWidth(), context.guiHeight(), 0xFF101010);
    }
}
