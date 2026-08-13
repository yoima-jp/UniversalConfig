package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.nio.file.Path;

public final class ProfileRenameScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int FIELD_Y = 64;
    private final Screen parent;
    private final Path profilePath;
    private final String initialName;
    private final Runnable onRenamed;
    private TextFieldWidget nameField;
    private ITextComponent status = StringTextComponent.EMPTY;

    public ProfileRenameScreen(Screen parent, Path profilePath, String initialName, Runnable onRenamed) {
        super(new TranslationTextComponent("screen.universal_config.profile_rename_title"));
        this.parent = parent;
        this.profilePath = profilePath;
        this.initialName = initialName;
        this.onRenamed = onRenamed;
    }

    @Override
    protected void init() {
        int left = (width - FORM_WIDTH) / 2;
        nameField = new TextFieldWidget(font, left, FIELD_Y, FORM_WIDTH, 20,
                new TranslationTextComponent("screen.universal_config.profile_name_placeholder"));
        nameField.setMaxLength(128);
        nameField.setValue(initialName == null ? "" : initialName);
        addButton(nameField);
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.rename"), button -> rename())
                .bounds(width / 2 - 104, height - 32, 100, 20).build());
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.back"),
                        button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 32, 100, 20).build());
        setFocused(nameField);
    }

    private void rename() {
        try {
            if (nameField.getValue().trim().isEmpty()) {
                status = new TranslationTextComponent("screen.universal_config.profile_name_required");
                return;
            }
            ProfileService service = ScreenUtil.service();
            service.renameProfile(profilePath, nameField.getValue());
            onRenamed.run();
            minecraft.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = new TranslationTextComponent("screen.universal_config.rename_failed");
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int left = (width - FORM_WIDTH) / 2;
        drawCenteredString(context, font, title, width / 2, 18, 0xFFFFFF);
        drawString(context, font, new TranslationTextComponent("screen.universal_config.profile_rename_label"),
                left, 44, 0xDDDDDD);
        drawString(context, font, status, left, height - 52, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}