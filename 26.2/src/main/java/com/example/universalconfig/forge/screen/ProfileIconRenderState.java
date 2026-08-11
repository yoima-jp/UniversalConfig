package com.example.universalconfig.forge.screen;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record ProfileIconRenderState(Identifier modelId, TrackingItemStackRenderState itemStackRenderState,
                                     int x0, int y0, int x1, int y1,
                                     @Nullable ScreenRectangle scissorArea,
                                     @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
    public ProfileIconRenderState(Identifier modelId, TrackingItemStackRenderState itemStackRenderState,
                                  int x0, int y0, int x1, int y1,
                                  @Nullable ScreenRectangle scissorArea) {
        this(modelId, itemStackRenderState, x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        return Math.min(x1 - x0, y1 - y0);
    }
}
