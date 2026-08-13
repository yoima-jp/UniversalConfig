package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class ProfileRenameScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int FIELD_Y = 64;
    private final Screen parent;
    private final Path profilePath;
    private final String initialName;
    private final Runnable onRenamed;
    private EditBox nameField;
    private Component status = Component.empty();

    public ProfileRenameScreen(Screen parent, Path profilePath, String initialName, Runnable onRenamed) {
        super(Component.translatable("screen.universal_config.profile_rename_title"));
        this.parent = parent;
        this.profilePath = profilePath;
        this.initialName = initialName;
        this.onRenamed = onRenamed;
    }

    @Override
    protected void init() {
        int left = (width - FORM_WIDTH) / 2;
        nameField = new EditBox(font, left, FIELD_Y, FORM_WIDTH, 20,
                Component.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setMaxLength(128);
        nameField.setValue(initialName == null ? "" : initialName);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.rename"), button -> rename())
                .bounds(width / 2 - 104, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.back"),
                        button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 32, 100, 20).build());
        setInitialFocus(nameField);
    }

    private void rename() {
        try {
            if (nameField.getValue().trim().isBlank()) {
                status = Component.translatable("screen.universal_config.profile_name_required");
                return;
            }
            ProfileService service = ScreenUtil.service();
            service.renameProfile(profilePath, nameField.getValue());
            onRenamed.run();
            minecraft.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = Component.translatable("screen.universal_config.rename_failed");
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int left = (width - FORM_WIDTH) / 2;
        context.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        context.drawString(font, Component.translatable("screen.universal_config.profile_rename_label"),
                left, 44, 0xDDDDDD);
        context.drawString(font, status, left, height - 52, 0xFF7777);
        ScreenUtil.renderWidgets(this, context, mouseX, mouseY, delta);
    }
}
