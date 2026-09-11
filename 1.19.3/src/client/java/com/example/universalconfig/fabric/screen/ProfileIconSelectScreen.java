package com.example.universalconfig.fabric.screen;

import com.example.universalconfig.core.ProfileIcon;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.function.Consumer;

final class ProfileIconSelectScreen extends Screen {
    private static final int BUTTON_SIZE = 34;
    private static final int BUTTON_GAP = 6;
    private static final int COLUMNS = 4;
    private static final String[] ICON_IDS = {
            ProfileIcon.GRASS_BLOCK, ProfileIcon.CRAFTING_TABLE,
            ProfileIcon.BOOKSHELF, ProfileIcon.COBBLESTONE,
            ProfileIcon.TNT, ProfileIcon.CHEST,
            ProfileIcon.FURNACE, ProfileIcon.DIAMOND_BLOCK
    };

    private final Screen parent;
    private final Consumer<String> selectionConsumer;
    private String selectedIconId;

    ProfileIconSelectScreen(Screen parent, String selectedIconId, Consumer<String> selectionConsumer) {
        super(Text.translatable("screen.universal_config.profile_icon_select_title"));
        this.parent = parent;
        this.selectedIconId = ProfileIcon.normalize(selectedIconId);
        this.selectionConsumer = selectionConsumer;
    }

    @Override
    protected void init() {
        int gridWidth = COLUMNS * BUTTON_SIZE + (COLUMNS - 1) * BUTTON_GAP;
        int left = (width - gridWidth) / 2;
        int top = 52;
        for (int index = 0; index < ICON_IDS.length; index++) {
            String iconId = ICON_IDS[index];
            int x = left + index % COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            int y = top + index / COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            addDrawableChild(new IconButton(x, y, BUTTON_SIZE, BUTTON_SIZE, iconLabel(iconId), button -> select(iconId)));
        }
        addDrawableChild(ScreenUtil.buttonBuilder(Text.translatable("screen.universal_config.back"), button -> close())
                .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    private void select(String iconId) {
        selectedIconId = iconId;
        selectionConsumer.accept(iconId);
        client.setScreen(parent);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        ScreenUtil.drawCenteredTextWithShadow(context, textRenderer, title, width / 2, 20, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        int gridWidth = COLUMNS * BUTTON_SIZE + (COLUMNS - 1) * BUTTON_GAP;
        int left = (width - gridWidth) / 2;
        int top = 52;
        for (int index = 0; index < ICON_IDS.length; index++) {
            int x = left + index % COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            int y = top + index / COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            ScreenUtil.drawItem(context, iconStack(ICON_IDS[index]), x + 9, y + 9);
        }
        int selectedIndex = Arrays.asList(ICON_IDS).indexOf(selectedIconId);
        if (selectedIndex >= 0) {
            int x = left + selectedIndex % COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            int y = top + selectedIndex / COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            ScreenUtil.fill(context, x, y, x + BUTTON_SIZE, y + 1, 0xFFFFFFFF);
            ScreenUtil.fill(context, x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
            ScreenUtil.fill(context, x, y, x + 1, y + BUTTON_SIZE, 0xFFFFFFFF);
            ScreenUtil.fill(context, x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
        }
    }

    private Text iconLabel(String iconId) {
        return Text.translatable("screen.universal_config.profile_icon_" + iconId);
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

    private static final class IconButton extends ButtonWidget {
        private final Text narrationLabel;
        private final int tileX;
        private final int tileY;
        private final int tileWidth;
        private final int tileHeight;

        private IconButton(int x, int y, int width, int height, Text narrationLabel, PressAction onPress) {
            super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
            this.narrationLabel = narrationLabel;
            this.tileX = x;
            this.tileY = y;
            this.tileWidth = width;
            this.tileHeight = height;
        }

        @Override
        public void renderButton(MatrixStack context, int mouseX, int mouseY, float delta) {
            int borderColor = !active ? 0xFF505050 : (isHovered() ? 0xFFB0B0B0 : 0xFF707070);
            int backgroundColor = !active ? 0xFF202020 : (isHovered() ? 0xFF505050 : 0xFF303030);
            ScreenUtil.fill(context, tileX, tileY, tileX + tileWidth, tileY + tileHeight, borderColor);
            ScreenUtil.fill(context, tileX + 1, tileY + 1, tileX + tileWidth - 1, tileY + tileHeight - 1, backgroundColor);
        }

        @Override
        protected MutableText getNarrationMessage() {
            return narrationLabel.copy();
        }
    }
}
