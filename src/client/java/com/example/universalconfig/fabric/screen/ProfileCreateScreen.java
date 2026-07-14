package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileCreateOptions;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ProfileCreateScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;
    private boolean includeKeybinds = true;
    private boolean includeClientOptions = true;
    private boolean includeModConfigs = true;
    private Text status = Text.empty();

    public ProfileCreateScreen(Screen parent) {
        super(Text.literal("プロファイル作成"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, width / 2 - 150, 52, 300, 20, Text.literal("name"));
        nameField.setText("Survival Main");
        addDrawableChild(nameField);
        descriptionField = new TextFieldWidget(textRenderer, width / 2 - 150, 92, 300, 20, Text.literal("description"));
        descriptionField.setText("キー設定とMod設定");
        addDrawableChild(descriptionField);

        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_keybinds", includeKeybinds), button -> {
            includeKeybinds = !includeKeybinds;
            button.setMessage(toggleText("screen.universal_config.target_keybinds", includeKeybinds));
        }).dimensions(width / 2 - 150, 126, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_client", includeClientOptions), button -> {
            includeClientOptions = !includeClientOptions;
            button.setMessage(toggleText("screen.universal_config.target_client", includeClientOptions));
        }).dimensions(width / 2 + 5, 126, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_mods", includeModConfigs), button -> {
            includeModConfigs = !includeModConfigs;
            button.setMessage(toggleText("screen.universal_config.target_mods", includeModConfigs));
        }).dimensions(width / 2 - 150, 152, 300, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), button -> create())
                .dimensions(width / 2 - 104, height - 32, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("戻る"), button -> client.setScreen(parent))
                .dimensions(width / 2 + 4, height - 32, 100, 20).build());
    }

    private Text toggleText(String labelKey, boolean enabled) {
        return Text.translatable("screen.universal_config.include_toggle",
                Text.translatable(labelKey),
                Text.translatable(enabled
                        ? "screen.universal_config.include_enabled"
                        : "screen.universal_config.include_disabled"));
    }

    private void create() {
        try {
            ProfileCreateOptions options = new ProfileCreateOptions();
            options.name = nameField.getText();
            options.description = descriptionField.getText();
            options.includeKeybinds = includeKeybinds;
            options.includeClientOptions = includeClientOptions;
            options.includeModConfigs = includeModConfigs;
            ProfileService service = ScreenUtil.service();
            service.createProfile(ScreenUtil.instancePath(), options, ScreenUtil.environment());
            client.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, "プロファイル名", width / 2 - 150, 40, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, "説明", width / 2 - 150, 80, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, status, width / 2 - 150, 186, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}
