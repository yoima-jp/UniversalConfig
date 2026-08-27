package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.util.math.MatrixStack;
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
    private Text status = ScreenUtil.empty();

    public ProfileRenameScreen(Screen parent, Path profilePath, String initialName, Runnable onRenamed) {
        super(ScreenUtil.translatable("screen.universal_config.profile_rename_title"));
        this.parent = parent;
        this.profilePath = profilePath;
        this.initialName = initialName;
        this.onRenamed = onRenamed;
    }

    @Override
    protected void init() {
        int left = (width - FORM_WIDTH) / 2;
        nameField = new TextFieldWidget(textRenderer, left, FIELD_Y, FORM_WIDTH, 20,
                ScreenUtil.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setMaxLength(128);
        nameField.setText(initialName == null ? "" : initialName);
        addChild(nameField);
        addButton(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.rename"), button -> rename())
                .dimensions(width / 2 - 104, height - 32, 100, 20).build());
        addButton(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.back"), button -> client.openScreen(parent))
                .dimensions(width / 2 + 4, height - 32, 100, 20).build());
        nameField.setFocusUnlocked(true);
        setInitialFocus(nameField);
    }

    private void rename() {
        try {
            if (nameField.getText().trim().isBlank()) {
                status = ScreenUtil.translatable("screen.universal_config.profile_name_required");
                return;
            }
            ProfileService service = ScreenUtil.service();
            service.renameProfile(profilePath, nameField.getText());
            onRenamed.run();
            client.openScreen(parent);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.translatable("screen.universal_config.rename_failed");
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int left = (width - FORM_WIDTH) / 2;
        ScreenUtil.drawCenteredTextWithShadow(context, textRenderer, title, width / 2, 18, 0xFFFFFF);
        ScreenUtil.drawTextWithShadow(context, textRenderer,
                ScreenUtil.translatable("screen.universal_config.profile_rename_label"), left, 44, 0xDDDDDD);
        ScreenUtil.drawTextWithShadow(context, textRenderer, status, left, height - 52, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}

