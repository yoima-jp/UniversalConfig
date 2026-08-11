package com.example.universalconfig.forge;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.GeneratedFileCleaner;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.forge.screen.ProfileListScreen;
import net.minecraft.client.util.InputMappings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

@Mod(UniversalConfigMod.MOD_ID)
public final class UniversalConfigMod {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final ResourceLocation TITLE_SCREEN_BUTTON_TEXTURE =
            new ResourceLocation(MOD_ID, "title_screen_button.png");
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    private static final int TITLE_SCREEN_BOTTOM_BRANDING_CLEARANCE = 54;

    private static KeyBinding openKey;
    private boolean pendingImportLogged;
    private boolean startupChangedOptions;

    public UniversalConfigMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            runStartupImport();
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(this::clientSetup);
            MinecraftForge.EVENT_BUS.register(this);
            ModLoadingContext.get().registerExtensionPoint(
                    ExtensionPoint.CONFIGGUIFACTORY,
                    () -> (minecraft, parent) -> new ProfileListScreen(parent));
        });
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    private void registerKeyBindings() {
        openKey = new KeyBinding(
                "key.universal_config.open",
                InputMappings.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.universal_config"
        );
        ClientRegistry.registerKeyBinding(openKey);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        registerKeyBindings();
        if (!startupChangedOptions) {
            return;
        }
        event.enqueueWork(() -> {
            Path optionsPath = UniversalConfigPaths.optionsFile(FMLPaths.GAMEDIR.get());
            try {
                MinecraftOptionsReloader.reloadFromDisk(optionsPath);
            } catch (RuntimeException ex) {
                FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", optionsPath, "startup reload failed", ex);
            }
        });
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || openKey == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (openKey.consumeClick()) {
            if (minecraft.screen == null || minecraft.screen instanceof MainMenuScreen) {
                minecraft.setScreen(new ProfileListScreen(minecraft.screen));
            }
        }
    }

    @SubscribeEvent
    public void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof MainMenuScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        reloadStartupOptionsAtTitleScreen();
        logPendingImportStateOnce();
        ITextComponent narration = new TranslationTextComponent("button.universal_config.open");
        int y = event.getGui().height - TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_BOTTOM_BRANDING_CLEARANCE;
        IconButton button = new IconButton(TITLE_SCREEN_BUTTON_MARGIN, y,
                ignored -> minecraft.setScreen(new ProfileListScreen(event.getGui())), narration);
        event.addWidget(button);
    }

    private void reloadStartupOptionsAtTitleScreen() {
        if (!startupChangedOptions) {
            return;
        }
        Path optionsPath = UniversalConfigPaths.optionsFile(FMLPaths.GAMEDIR.get());
        try {
            // FMLClientSetup applies non-language settings early. At the title screen the language metadata is
            // guaranteed to be loaded, so this second synchronization also covers first-start profiles in every locale.
            MinecraftOptionsReloader.reloadFromDisk(optionsPath);
            startupChangedOptions = false;
        } catch (RuntimeException ex) {
            FileOperationLogger.failure("RELOAD_CLIENT_OPTIONS", optionsPath,
                    "title screen synchronization failed", ex);
        }
    }

    private void runStartupImport() {
        Path gameDirectory = FMLPaths.GAMEDIR.get();
        String operation = "STARTUP_INITIALIZE";
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory);
            ProfileService service = new ProfileService(settings);
            try {
                new GeneratedFileCleaner(settings).cleanup();
            } catch (RuntimeException ex) {
                FileOperationLogger.failure("CLEANUP", settings.rootDirectory(), "startup cleanup failed", ex);
            }
            ProfileService.ApplyResult result;
            if (service.readPendingImport(gameDirectory) != null) {
                operation = "STARTUP_PENDING_IMPORT";
                result = service.applyPendingImport(gameDirectory, ForgeEnvironmentDetector.detect(gameDirectory));
            } else {
                operation = "STARTUP_DEFAULT_PROFILE";
                result = service.applyDefaultProfileOnFirstStart(
                        gameDirectory, ForgeEnvironmentDetector.detect(gameDirectory));
            }
            startupChangedOptions = result != null;
            FileOperationLogger.info(operation, gameDirectory,
                    result == null ? "not applied" : "complete backup=" + result.backupPath());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure(operation, gameDirectory, "failed", ex);
        }
    }

    private void logPendingImportStateOnce() {
        if (pendingImportLogged) {
            return;
        }
        pendingImportLogged = true;
        Path gameDirectory = FMLPaths.GAMEDIR.get();
        try {
            UniversalConfigSettings settings = UniversalConfigPaths.loadOrCreateSettings(gameDirectory);
            FileOperationLogger.info("TITLE_SCREEN_READY", gameDirectory,
                    "settingsRoot=" + settings.rootDirectory());
        } catch (UniversalConfigException | RuntimeException ex) {
            FileOperationLogger.failure("TITLE_SCREEN_READY", gameDirectory, "failed", ex);
        }
    }

    private static final class IconButton extends Button {
        private final ITextComponent narrationMessage;

        private IconButton(int x, int y, IPressable onPress, ITextComponent narrationMessage) {
            super(x, y, TITLE_SCREEN_BUTTON_SIZE, TITLE_SCREEN_BUTTON_SIZE,
                    StringTextComponent.EMPTY, ignored -> { });
            this.onPress = onPress;
            this.narrationMessage = narrationMessage;
        }

        private final IPressable onPress;

        @Override
        public void onPress() {
            onPress.onPress(this);
        }

        @Override
        protected IFormattableTextComponent createNarrationMessage() {
            return new TranslationTextComponent("gui.narrate.button", narrationMessage);
        }

        @Override
        public void renderButton(MatrixStack graphics, int mouseX, int mouseY, float partialTick) {
            super.renderButton(graphics, mouseX, mouseY, partialTick);
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            Minecraft.getInstance().getTextureManager().bind(TITLE_SCREEN_BUTTON_TEXTURE);
            AbstractGui.blit(graphics,
                    x + TITLE_SCREEN_ICON_PADDING,
                    y + TITLE_SCREEN_ICON_PADDING,
                    iconSize, iconSize,
                    0.0F, 0.0F, 128, 128, 128, 128);
        }

        @Override
        public void renderToolTip(MatrixStack graphics, int mouseX, int mouseY) {
            if (Minecraft.getInstance().screen != null) {
                Minecraft.getInstance().screen.renderTooltip(graphics, narrationMessage, mouseX, mouseY);
            }
        }
    }
}
