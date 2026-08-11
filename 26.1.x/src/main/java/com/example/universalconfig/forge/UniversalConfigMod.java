package com.example.universalconfig.forge;

import com.example.universalconfig.core.FileOperationLogger;
import com.example.universalconfig.core.GeneratedFileCleaner;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.example.universalconfig.core.UniversalConfigFormat;
import com.example.universalconfig.core.UniversalConfigPaths;
import com.example.universalconfig.core.UniversalConfigSettings;
import com.example.universalconfig.forge.screen.ProfileListScreen;
import com.example.universalconfig.forge.screen.ProfileIconRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

@Mod(UniversalConfigMod.MOD_ID)
public final class UniversalConfigMod {
    public static final String MOD_ID = UniversalConfigFormat.MOD_ID;
    private static final Identifier TITLE_SCREEN_BUTTON_TEXTURE =
            Identifier.fromNamespaceAndPath(MOD_ID, "title_screen_button.png");
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static final int TITLE_SCREEN_BUTTON_SIZE = 20;
    private static final int TITLE_SCREEN_ICON_PADDING = 3;
    private static final int TITLE_SCREEN_BUTTON_MARGIN = 4;
    private static final int TITLE_SCREEN_BOTTOM_BRANDING_CLEARANCE = 54;

    private static KeyMapping openKey;
    private boolean pendingImportLogged;
    private boolean startupChangedOptions;

    public UniversalConfigMod(FMLJavaModLoadingContext context) {
        runStartupImport();
        RegisterKeyMappingsEvent.BUS.addListener(this::registerKeyMappings);
        // プロフィール一覧のアイコンを高解像度描画するPiPレンダラーを登録（modバス）。
        RegisterPictureInPictureRendererEvent.BUS.addListener(this::registerPictureInPictureRenderers);
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(this::clientSetup);
        TickEvent.ClientTickEvent.Post.BUS.addListener(this::onClientTick);
        ScreenEvent.Init.Post.BUS.addListener(this::onScreenInit);
        MinecraftForge.registerConfigScreen((minecraft, parent) -> new ProfileListScreen(parent));
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        openKey = new KeyMapping(
                "key.universal_config.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        );
        event.register(openKey);
    }

    private void registerPictureInPictureRenderers(RegisterPictureInPictureRendererEvent event) {
        // 26.1.2はGUIアイテムアトラスが16px固定のため、28px等の非整数倍拡大でアイコンが荒れる。
        // バニラのOversizedItemRendererはモデルAABBが16px超のときしか使われないため、通常ブロック
        // モデルのアイコンは独自PiPレンダラー経由でターゲット解像度へ直接描画する。
        event.register(new ProfileIconRenderer(event.getBufferSource()));
    }

    private void clientSetup(FMLClientSetupEvent event) {
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

    public void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (openKey == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (openKey.consumeClick()) {
            if (minecraft.screen == null || minecraft.screen instanceof TitleScreen) {
                minecraft.setScreen(new ProfileListScreen(minecraft.screen));
            }
        }
    }

    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        reloadStartupOptionsAtTitleScreen();
        logPendingImportStateOnce();
        Component narration = Component.translatable("button.universal_config.open");
        int y = event.getScreen().height - TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_BOTTOM_BRANDING_CLEARANCE;
        IconButton button = new IconButton(TITLE_SCREEN_BUTTON_MARGIN, y,
                ignored -> minecraft.setScreen(new ProfileListScreen(event.getScreen())), narration);
        button.setTooltip(Tooltip.create(narration));
        event.addListener(button);
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
        private final Component narrationMessage;

        private IconButton(int x, int y, OnPress onPress, Component narrationMessage) {
            super(x, y, TITLE_SCREEN_BUTTON_SIZE, TITLE_SCREEN_BUTTON_SIZE,
                    Component.empty(), onPress, DEFAULT_NARRATION);
            this.narrationMessage = narrationMessage;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", narrationMessage);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            int iconSize = TITLE_SCREEN_BUTTON_SIZE - TITLE_SCREEN_ICON_PADDING * 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, TITLE_SCREEN_BUTTON_TEXTURE,
                    getX() + TITLE_SCREEN_ICON_PADDING,
                    getY() + TITLE_SCREEN_ICON_PADDING,
                    0.0F, 0.0F, iconSize, iconSize, 128, 128, 128, 128);
        }
    }
}
