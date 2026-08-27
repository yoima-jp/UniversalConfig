package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileCreateOptions;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

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
    private CheckboxWidget keybindsCheckbox;
    private CheckboxWidget clientOptionsCheckbox;
    private CheckboxWidget modConfigsCheckbox;
    private String selectedIconId = ProfileIcon.GRASS_BLOCK;
    private Text status = ScreenUtil.empty();

    public ProfileCreateScreen(Screen parent) {
        super(ScreenUtil.translatable("screen.universal_config.profile_create_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String currentName = nameField == null ? "Survival Main" : nameField.getText();
        // 説明は利用者が入力した内容だけを保存する。翻訳済みの初期文を入れると、
        // プロフィール作成時の表示言語が共有データへ固定されるため、初期状態ではプレースホルダーだけを表示する。
        String currentDescription = descriptionField == null ? "" : descriptionField.getText();
        boolean includeKeybinds = keybindsCheckbox == null || keybindsCheckbox.isChecked();
        boolean includeClientOptions = clientOptionsCheckbox == null || clientOptionsCheckbox.isChecked();
        // configには環境固有の値が含まれることがあるため、保存範囲の広い項目は初期状態で任意選択にする。
        // 画面再初期化時は、すでに選択された状態を引き続き保持する。
        boolean includeModConfigs = modConfigsCheckbox != null && modConfigsCheckbox.isChecked();
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        addButton(new BlockIconButton(formLeft, NAME_FIELD_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                ScreenUtil.translatable("screen.universal_config.profile_icon_change", iconLabel(selectedIconId)),
                button -> client.openScreen(new ProfileIconSelectScreen(this, selectedIconId,
                        iconId -> selectedIconId = iconId))));
        nameField = new TextFieldWidget(textRenderer,
                formLeft + ICON_BUTTON_SIZE + FIELD_GAP, NAME_FIELD_Y,
                FORM_WIDTH - ICON_BUTTON_SIZE - FIELD_GAP, 20,
                ScreenUtil.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setText(currentName);
        addChild(nameField);
        descriptionField = new TextFieldWidget(textRenderer, formLeft, 92, FORM_WIDTH, 20,
                ScreenUtil.translatable("screen.universal_config.profile_description_placeholder"));
        descriptionField.setMaxLength(DESCRIPTION_MAX_LENGTH);
        descriptionField.setText(currentDescription);
        addChild(descriptionField);

        keybindsCheckbox = new CheckboxWidget(formLeft, CHECKBOX_Y, FORM_WIDTH, 20,
                ScreenUtil.translatable("screen.universal_config.target_keybinds"), includeKeybinds, true);
        clientOptionsCheckbox = new CheckboxWidget(formLeft, CHECKBOX_Y + CHECKBOX_STEP, FORM_WIDTH, 20,
                ScreenUtil.translatable("screen.universal_config.target_client"), includeClientOptions, true);
        modConfigsCheckbox = new CheckboxWidget(formLeft, CHECKBOX_Y + CHECKBOX_STEP * 2, FORM_WIDTH, 20,
                ScreenUtil.translatable("screen.universal_config.target_mods"), includeModConfigs, true);
        addButton(keybindsCheckbox);
        addButton(clientOptionsCheckbox);
        addButton(modConfigsCheckbox);
        addButton(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.save"), button -> create())
                .dimensions(width / 2 - 104, height - 32, 100, 20).build());
        addButton(ScreenUtil.buttonBuilder(ScreenUtil.translatable("screen.universal_config.back"), button -> client.openScreen(parent))
                .dimensions(width / 2 + 4, height - 32, 100, 20).build());
    }

    private Text iconLabel(String iconId) {
        return ScreenUtil.translatable("screen.universal_config.profile_icon_" + iconId);
    }

    private void create() {
        try {
            ProfileCreateOptions options = new ProfileCreateOptions();
            options.name = nameField.getText();
            options.description = descriptionField.getText();
            options.icon = selectedIconId;
            options.includeKeybinds = keybindsCheckbox.isChecked();
            options.includeClientOptions = clientOptionsCheckbox.isChecked();
            options.includeModConfigs = modConfigsCheckbox.isChecked();
            ProfileService service = ScreenUtil.service();
            service.createProfile(ScreenUtil.instancePath(), options, ScreenUtil.environment());
            client.openScreen(parent);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        ScreenUtil.drawCenteredTextWithShadow(context, textRenderer, title, width / 2, 18, 0xFFFFFF);
        ScreenUtil.drawTextWithShadow(context, textRenderer, ScreenUtil.translatable("screen.universal_config.profile_name_label"),
                formLeft, 40, 0xDDDDDD);
        ScreenUtil.drawTextWithShadow(context, textRenderer, ScreenUtil.translatable("screen.universal_config.profile_description_label"),
                formLeft, 80, 0xDDDDDD);
        ScreenUtil.drawTextWithShadow(context, textRenderer, ScreenUtil.translatable("screen.universal_config.profile_save_contents"),
                formLeft, SAVE_CONTENTS_LABEL_Y, 0xDDDDDD);
        // 高さ240pxのGUIでも、エラー文と下部ボタンの間に読みやすい余白を確保する。
        int statusY = Math.min(STATUS_PREFERRED_Y, height - 32 - STATUS_FOOTER_GAP);
        ScreenUtil.drawTextWithShadow(context, textRenderer, status, formLeft, statusY, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
        ScreenUtil.drawItem(context, iconStack(selectedIconId), formLeft + 2, NAME_FIELD_Y + 2);
    }

    private ItemStack iconStack(String iconId) {
        return switch (ProfileIcon.normalize(iconId)) {
            case ProfileIcon.CRAFTING_TABLE -> new ItemStack(Blocks.CRAFTING_TABLE);
            case ProfileIcon.BOOKSHELF -> new ItemStack(Blocks.BOOKSHELF);
            case ProfileIcon.COBBLESTONE -> new ItemStack(Blocks.COBBLESTONE);
            case ProfileIcon.TNT -> new ItemStack(Blocks.TNT);
            case ProfileIcon.CHEST -> new ItemStack(Blocks.CHEST);
            case ProfileIcon.FURNACE -> new ItemStack(Blocks.FURNACE);
            case ProfileIcon.DIAMOND_BLOCK -> new ItemStack(Blocks.DIAMOND_BLOCK);
            default -> new ItemStack(Blocks.GRASS_BLOCK);
        };
    }

    /**
     * アイコンだけのボタンでも、ナレーターにはブロック名を伝える。
     * 見た目は名前欄の左に小さく統合し、支援技術には現在のブロック名と変更操作を伝える。
     */
    private static final class BlockIconButton extends ButtonWidget {
        private final Text narrationLabel;

        private BlockIconButton(int x, int y, int width, int height, Text narrationLabel, PressAction onPress) {
            super(x, y, width, height, ScreenUtil.empty(), onPress);
            this.narrationLabel = narrationLabel;
        }

        @Override
        protected MutableText getNarrationMessage() {
            return narrationLabel.copy();
        }
    }
}

