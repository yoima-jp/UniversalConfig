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
    private static final int FORM_WIDTH = 300;
    private static final int FORM_LEFT_OFFSET = FORM_WIDTH / 2;
    private static final int SCOPE_Y = 186;
    private static final int STATUS_PREFERRED_Y = 206;
    private static final int STATUS_FOOTER_GAP = 12;

    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;
    private boolean includeKeybinds = true;
    private boolean includeClientOptions = true;
    private boolean includeModConfigs = true;
    private Text status = Text.empty();

    public ProfileCreateScreen(Screen parent) {
        super(Text.translatable("screen.universal_config.profile_create_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(textRenderer, width / 2 - FORM_LEFT_OFFSET, 52, FORM_WIDTH, 20,
                Text.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setText("Survival Main");
        addDrawableChild(nameField);
        descriptionField = new TextFieldWidget(textRenderer, width / 2 - FORM_LEFT_OFFSET, 92, FORM_WIDTH, 20,
                Text.translatable("screen.universal_config.profile_description_placeholder"));
        descriptionField.setText(Text.translatable("screen.universal_config.profile_default_description").getString());
        addDrawableChild(descriptionField);

        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_keybinds", includeKeybinds), button -> {
            includeKeybinds = !includeKeybinds;
            button.setMessage(toggleText("screen.universal_config.target_keybinds", includeKeybinds));
        }).dimensions(width / 2 - FORM_LEFT_OFFSET, 126, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_client", includeClientOptions), button -> {
            includeClientOptions = !includeClientOptions;
            button.setMessage(toggleText("screen.universal_config.target_client", includeClientOptions));
        }).dimensions(width / 2 + 5, 126, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(toggleText("screen.universal_config.target_mods", includeModConfigs), button -> {
            includeModConfigs = !includeModConfigs;
            button.setMessage(toggleText("screen.universal_config.target_mods", includeModConfigs));
        }).dimensions(width / 2 - FORM_LEFT_OFFSET, 152, FORM_WIDTH, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.save"), button -> create())
                .dimensions(width / 2 - 104, height - 32, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.universal_config.back"), button -> client.setScreen(parent))
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
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.universal_config.profile_name_label"),
                formLeft, 40, 0xDDDDDD);
        context.drawTextWithShadow(textRenderer, Text.translatable("screen.universal_config.profile_description_label"),
                formLeft, 80, 0xDDDDDD);
        // 翻訳文がフォーム幅を超えても、下部ボタンの領域へ流れ込ませない。
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(Text.translatable("screen.universal_config.profile_scope").getString(), FORM_WIDTH),
                formLeft, SCOPE_Y, 0xBBBBBB);
        // 高さ240pxのGUIでも、エラー文と下部ボタンの間に読みやすい余白を確保する。
        int statusY = Math.min(STATUS_PREFERRED_Y, height - 32 - STATUS_FOOTER_GAP);
        context.drawTextWithShadow(textRenderer, status, formLeft, statusY, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
    }
}
