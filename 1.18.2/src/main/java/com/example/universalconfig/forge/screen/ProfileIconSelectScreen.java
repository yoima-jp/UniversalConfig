package com.example.universalconfig.forge.screen;

import com.example.universalconfig.core.ProfileIcon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

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
        super(new TranslatableComponent("screen.universal_config.profile_icon_select_title"));
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
            addRenderableWidget(new IconButton(x, y, BUTTON_SIZE, BUTTON_SIZE, iconLabel(iconId), button -> select(iconId)));
        }
        addRenderableWidget(Button.builder(new TranslatableComponent("screen.universal_config.back"), button -> onClose())
                .bounds(width / 2 - 100, height - 32, 200, 20).build());
    }

    private void select(String iconId) {
        selectedIconId = iconId;
        selectionConsumer.accept(iconId);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(PoseStack context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        drawCenteredString(context, font, title, width / 2, 20, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        int gridWidth = COLUMNS * BUTTON_SIZE + (COLUMNS - 1) * BUTTON_GAP;
        int left = (width - gridWidth) / 2;
        int top = 52;
        for (int index = 0; index < ICON_IDS.length; index++) {
            int x = left + index % COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            int y = top + index / COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            ScreenUtil.drawProfileIcon(x + 9, y + 9, 16, ICON_IDS[index]);
        }
        int selectedIndex = Arrays.asList(ICON_IDS).indexOf(selectedIconId);
        if (selectedIndex >= 0) {
            int x = left + selectedIndex % COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            int y = top + selectedIndex / COLUMNS * (BUTTON_SIZE + BUTTON_GAP);
            fill(context, x, y, x + BUTTON_SIZE, y + 1, 0xFFFFFFFF);
            fill(context, x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
        }
    }

    private Component iconLabel(String iconId) {
        return new TranslatableComponent("screen.universal_config.profile_icon_" + iconId);
    }

    private static final class IconButton extends Button {
        private final Component narrationLabel;

        private IconButton(int x, int y, int width, int height, Component narrationLabel, OnPress onPress) {
            super(x, y, width, height, TextComponent.EMPTY, onPress);
            this.narrationLabel = narrationLabel;
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return narrationLabel.copy();
        }
    }
}
