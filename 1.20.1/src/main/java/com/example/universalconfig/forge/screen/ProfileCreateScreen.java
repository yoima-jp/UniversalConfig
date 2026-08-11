package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.ProfileCreateOptions;
import com.example.universalconfig.core.ProfileIcon;
import com.example.universalconfig.core.ProfileService;
import com.example.universalconfig.core.UniversalConfigException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    private EditBox nameField;
    private EditBox descriptionField;
    private Checkbox keybindsCheckbox;
    private Checkbox clientOptionsCheckbox;
    private Checkbox modConfigsCheckbox;
    private String selectedIconId = ProfileIcon.GRASS_BLOCK;
    private Component status = Component.empty();

    public ProfileCreateScreen(Screen parent) {
        super(Component.translatable("screen.universal_config.profile_create_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String currentName = nameField == null ? "Survival Main" : nameField.getValue();
        // 説明は利用者が入力した内容だけを保存する。翻訳済みの初期文を入れると、
        // プロフィール作成時の表示言語が共有データへ固定されるため、初期状態ではプレースホルダーだけを表示する。
        String currentDescription = descriptionField == null ? "" : descriptionField.getValue();
        boolean includeKeybinds = keybindsCheckbox == null || keybindsCheckbox.selected();
        boolean includeClientOptions = clientOptionsCheckbox == null || clientOptionsCheckbox.selected();
        boolean includeModConfigs = modConfigsCheckbox == null || modConfigsCheckbox.selected();
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        addRenderableWidget(new BlockIconButton(formLeft, NAME_FIELD_Y, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE,
                Component.translatable("screen.universal_config.profile_icon_change", iconLabel(selectedIconId)),
                button -> minecraft.setScreen(new ProfileIconSelectScreen(this, selectedIconId,
                        iconId -> selectedIconId = iconId))));
        nameField = new EditBox(font,
                formLeft + ICON_BUTTON_SIZE + FIELD_GAP, NAME_FIELD_Y,
                FORM_WIDTH - ICON_BUTTON_SIZE - FIELD_GAP, 20,
                Component.translatable("screen.universal_config.profile_name_placeholder"));
        nameField.setValue(currentName);
        addRenderableWidget(nameField);
        descriptionField = new EditBox(font, formLeft, 92, FORM_WIDTH, 20,
                Component.translatable("screen.universal_config.profile_description_placeholder"));
        descriptionField.setMaxLength(DESCRIPTION_MAX_LENGTH);
        descriptionField.setValue(currentDescription);
        addRenderableWidget(descriptionField);

        keybindsCheckbox = new Checkbox(formLeft, CHECKBOX_Y, FORM_WIDTH, 20,
                Component.translatable("screen.universal_config.target_keybinds"), includeKeybinds, true);
        clientOptionsCheckbox = new Checkbox(formLeft, CHECKBOX_Y + CHECKBOX_STEP, FORM_WIDTH, 20,
                Component.translatable("screen.universal_config.target_client"), includeClientOptions, true);
        modConfigsCheckbox = new Checkbox(formLeft, CHECKBOX_Y + CHECKBOX_STEP * 2, FORM_WIDTH, 20,
                Component.translatable("screen.universal_config.target_mods"), includeModConfigs, true);
        addRenderableWidget(keybindsCheckbox);
        addRenderableWidget(clientOptionsCheckbox);
        addRenderableWidget(modConfigsCheckbox);
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.save"), button -> create())
                .bounds(width / 2 - 104, height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.universal_config.back"), button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    private Component iconLabel(String iconId) {
        return Component.translatable("screen.universal_config.profile_icon_" + iconId);
    }

    private void create() {
        try {
            ProfileCreateOptions options = new ProfileCreateOptions();
            options.name = nameField.getValue();
            options.description = descriptionField.getValue();
            options.icon = selectedIconId;
            options.includeKeybinds = keybindsCheckbox.selected();
            options.includeClientOptions = clientOptionsCheckbox.selected();
            options.includeModConfigs = modConfigsCheckbox.selected();
            ProfileService service = ScreenUtil.service();
            service.createProfile(ScreenUtil.instancePath(), options, ScreenUtil.environment());
            minecraft.setScreen(parent);
        } catch (UniversalConfigException ex) {
            status = ScreenUtil.errorText(ex);
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int formLeft = width / 2 - FORM_LEFT_OFFSET;
        context.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        context.drawString(font, Component.translatable("screen.universal_config.profile_name_label"),
                formLeft, 40, 0xDDDDDD);
        context.drawString(font, Component.translatable("screen.universal_config.profile_description_label"),
                formLeft, 80, 0xDDDDDD);
        context.drawString(font, Component.translatable("screen.universal_config.profile_save_contents"),
                formLeft, SAVE_CONTENTS_LABEL_Y, 0xDDDDDD);
        // 高さ240pxのGUIでも、エラー文と下部ボタンの間に読みやすい余白を確保する。
        int statusY = Math.min(STATUS_PREFERRED_Y, height - 32 - STATUS_FOOTER_GAP);
        context.drawString(font, status, formLeft, statusY, 0xFF7777);
        super.render(context, mouseX, mouseY, delta);
        context.renderItem(iconStack(selectedIconId), formLeft + 2, NAME_FIELD_Y + 2);
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
    private static final class BlockIconButton extends Button {
        private final Component narrationLabel;

        private BlockIconButton(int x, int y, int width, int height, Component narrationLabel, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.narrationLabel = narrationLabel;
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return narrationLabel.copy();
        }
    }
}
