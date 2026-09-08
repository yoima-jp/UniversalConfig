package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;

public final class ProfileRenameScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int FIELD_Y = 64;
    private final Screen parent;
    private final Path profilePath;
    private final String initialName;
    private final Runnable onRenamed;
    private TextFieldWidget nameField;
    private Text status = Text.empty();

    public ProfileRenameScreen(Screen parent, Path profilePath, String initialName, Runnable onRenamed) {
        super(Text.translatable("screen.universal_config.profile_rename_title"));
        this.parent = parent;
        this.profilePath = profilePath;
        this.initialName = initialName;
        this.onRenamed = onRenamed;
    }

    @Override
    protected void init() {
        int left = (width - FORM_WIDTH) / 2;
        nameField = new TextFieldWidget(textRenderer, left, FIELD_Y, FORM_WIDTH, 20,
                Text.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setMaxLength(128);
        nameField.setText(initialName == null ? "" : initialName);
        addDrawableChild(nameField);
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.rename"), button -> rename())
                .dimensions(width / 2 - 104, height - 32, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.back"), button -> client.setScreen(parent))
                .dimensions(width / 2 + 4, height - 32, 100, 20).build());
        nameField.setFocused(true);
        setInitialFocus(nameField);
    }

    private void rename() {
        try {
            if (nameField.getText().trim().isBlank()) {
                status = Text.translatable("screen.universal_config.profile_name_required");
                return;
            }
            ProfileService service = ScreenUtil.service();
            service.renameProfile(profilePath, nameField.getText());
            onRenamed.run();
            client.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = Text.translatable("screen.universal_config.rename_failed");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ScreenUtil.renderBackground(this, context, mouseX, mouseY, delta);
        int left = (width - FORM_WIDTH) / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer,
                Text.translatable("screen.universal_config.profile_rename_label"), left, 44, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, status, left, height - 52, 0xFF7777);
        ScreenUtil.renderWidgets(this, context, mouseX, mouseY, delta);
    }
}
