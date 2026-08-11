package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.ProfileCreateOptions;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.CheckboxButton;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.IFormattableTextComponent;

public final class ProfileCreateScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int FORM_LEFT_OFFSET = FORM_WIDTH / 2;
    private static final int NAME_FIELD_Y = 52;
    private static final int ICON_BUTTON_SIZE = 20;
    private static final int FIELD_GAP = 4;
    private static final int DESCRIPTION_MAX_LENGTH = 512;
    private static final int SAVE_CONTENTS_LABEL_Y = 118;
    private static final int CHECKBOX_Y = 130;
    private static final int CHECKBOX_STEP = 20;
    private static final int STATUS_PREFERRED_Y = 194;
    private static final int STATUS_FOOTER_GAP = 12;

    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget descriptionField;
    private CheckboxButton keybindsCheckboxButton;
    private CheckboxButton clientOptionsCheckboxButton;
    private CheckboxButton modConfigsCheckboxButton;
    private String selectedIconId = ProfileIcon.GRASS_BLOCK;
    private ITextComponent status = StringTextComponent.EMPTY;

    public ProfileCreateScreen(Screen parent) {
        super(new TranslationTextComponent("screen.universal_config.profile_create_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String currentName = nameField == null ? "Survival Main" : nameField.getValue();
        // 説明は利用者が入力した内容だけを保存する。翻訳済みの初期文を入れると、
        // プロフィール作成時の表示言語が共有データへ固定されるため、初期状態ではプレースホルダーだけを表示する。
        String currentDescription = descriptionField == null ? "" : descriptionField.getValue();
        boolean includeKeybinds = keybindsCheckboxButton == null || keybindsCheckboxButton.selected();
        boolean includeClientOptions = clientOptionsCheckboxButton == null || clientOptionsCheckboxButton.selected();
        boolean includeModConfigs = modConfigsCheckboxButton == null || modConfigsCheckboxButton.selected();
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        addButton(new BlockIconButton(formLeft, NAME_FIELD_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                new TranslationTextComponent("screen.universal_config.profile_icon_change", iconLabel(selectedIconId)),
                button -> minecraft.setScreen(new ProfileIconSelectScreen(this, selectedIconId,
                        iconId -> selectedIconId = iconId))));
        nameField = new TextFieldWidget(font,
                formLeft + ICON_BUTTON_SIZE + FIELD_GAP, NAME_FIELD_Y,
                FORM_WIDTH - ICON_BUTTON_SIZE - FIELD_GAP, 20,
                new TranslationTextComponent("screen.universal_config.profile_name_placeholder"));
        nameField.setValue(currentName);
        addButton(nameField);
        descriptionField = new TextFieldWidget(font, formLeft, 92, FORM_WIDTH, 20,
                new TranslationTextComponent("screen.universal_config.profile_description_placeholder"));
        descriptionField.setMaxLength(DESCRIPTION_MAX_LENGTH);
        descriptionField.setValue(currentDescription);
        addButton(descriptionField);

        keybindsCheckboxButton = new CheckboxButton(formLeft, CHECKBOX_Y, FORM_WIDTH, 20,
                new TranslationTextComponent("screen.universal_config.target_keybinds"), includeKeybinds, true);
        clientOptionsCheckboxButton = new CheckboxButton(formLeft, CHECKBOX_Y + CHECKBOX_STEP, FORM_WIDTH, 20,
                new TranslationTextComponent("screen.universal_config.target_client"), includeClientOptions, true);
        modConfigsCheckboxButton = new CheckboxButton(formLeft, CHECKBOX_Y + CHECKBOX_STEP * 2, FORM_WIDTH, 20,
                new TranslationTextComponent("screen.universal_config.target_mods"), includeModConfigs, true);
        addButton(keybindsCheckboxButton);
        addButton(clientOptionsCheckboxButton);
        addButton(modConfigsCheckboxButton);
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.save"), button -> create())
                .bounds(width / 2 - 104, height - 32, 100, 20).build());
        addButton(Button.builder(new TranslationTextComponent("screen.universal_config.back"), button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    private ITextComponent iconLabel(String iconId) {
        return new TranslationTextComponent("screen.universal_config.profile_icon_" + iconId);
    }

    private void create() {
        try {
            ProfileCreateOptions options = new ProfileCreateOptions();
            options.name = nameField.getValue();
            options.description = descriptionField.getValue();
            options.icon = selectedIconId;
            options.includeKeybinds = keybindsCheckboxButton.selected();
            options.includeClientOptions = clientOptionsCheckboxButton.selected();
            options.includeModConfigs = modConfigsCheckboxButton.selected();
            ProfileService service = ScreenUtil.service();
            service.createProfile(ScreenUtil.instancePath(), options, ScreenUtil.environment());
            minecraft.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        drawCenteredString(context, font, title, width / 2, 18, 0xFFFFFF);
        drawString(context, font, new TranslationTextComponent("screen.universal_config.profile_name_label"),
                formLeft, 40, 0xDDDDDD);
        drawString(context, font, new TranslationTextComponent("screen.universal_config.profile_description_label"),
                formLeft, 80, 0xDDDDDD);
        drawString(context, font, new TranslationTextComponent("screen.universal_config.profile_save_contents"),
                formLeft, SAVE_CONTENTS_LABEL_Y, 0xDDDDDD);
        // 高さ240pxのGUIでも、エラー文と下部ボタンの間に読みやすい余白を確保する。
        int statusY = Math.min(STATUS_PREFERRED_Y, height - 32 - STATUS_FOOTER_GAP);
        drawString(context, font, status, formLeft, statusY, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
        minecraft.getItemRenderer().renderAndDecorateItem(iconStack(selectedIconId), formLeft + 2, NAME_FIELD_Y + 2);
    }

    private ItemStack iconStack(String iconId) {
        return ScreenUtil.iconStack(iconId);
    }

    /**
     * アイコンだけのボタンでも、ナレーターにはブロック名を伝える。
     * 見た目は名前欄の左に小さく統合し、支援技術には現在のブロック名と変更操作を伝える。
     */
    private static final class BlockIconButton extends Button {
        private final ITextComponent narrationLabel;

        private BlockIconButton(int x, int y, int width, int height, ITextComponent narrationLabel, IPressable onPress) {
            super(x, y, width, height, StringTextComponent.EMPTY, onPress);
            this.narrationLabel = narrationLabel;
        }

        @Override
        protected IFormattableTextComponent createNarrationMessage() {
            return narrationLabel.copy();
        }
    }
}
