package com.example.universalconfig.forge.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class ProfileIconRenderer extends PictureInPictureRenderer<ProfileIconRenderState> {
    private final Map<RenderTargetKey, IconTextureRenderer> renderersByTarget = new HashMap<>();

    @Override public Class<ProfileIconRenderState> getRenderStateClass() { return ProfileIconRenderState.class; }

    @Override
    public void prepare(ProfileIconRenderState state, GuiRenderState guiRenderState,
                        FeatureRenderDispatcher dispatcher, int guiScale) {
        // PiP renderers own one GPU texture. Isolate each model and size so icons submitted in one frame
        // cannot overwrite one another before the GUI blits them.
        RenderTargetKey key = new RenderTargetKey(state.modelId(), state.x1() - state.x0(), state.y1() - state.y0());
        renderersByTarget.computeIfAbsent(key, ignored -> new IconTextureRenderer())
                .prepare(state, guiRenderState, dispatcher, guiScale);
    }

    @Override protected void renderToTexture(ProfileIconRenderState state, PoseStack poseStack,
                                             SubmitNodeCollector submitNodeCollector) {
        throw new IllegalStateException("Profile icon rendering must use a model-specific renderer");
    }

    @Override public void close() {
        renderersByTarget.values().forEach(IconTextureRenderer::close);
        renderersByTarget.clear();
        super.close();
    }

    @Override protected String getTextureLabel() { return "universal_config_profile_icon"; }

    private record RenderTargetKey(Identifier modelId, int width, int height) {}

    private static final class IconTextureRenderer extends PictureInPictureRenderer<ProfileIconRenderState> {
        private Object modelOnTextureIdentity;

        @Override public Class<ProfileIconRenderState> getRenderStateClass() { return ProfileIconRenderState.class; }

        @Override protected void renderToTexture(ProfileIconRenderState state, PoseStack poseStack,
                                                 SubmitNodeCollector submitNodeCollector) {
            poseStack.scale(1.0F, -1.0F, -1.0F);
            TrackingItemStackRenderState itemState = state.itemStackRenderState();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.lighting().setupFor(itemState.usesBlockLight()
                    ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
            itemState.submit(poseStack, submitNodeCollector, 15728880, OverlayTexture.NO_OVERLAY, 0);
            modelOnTextureIdentity = itemState.getModelIdentity();
        }

        @Override protected float getTranslateY(int height, int guiScale) { return height / 2.0F; }
        @Override protected boolean textureIsReadyToBlit(ProfileIconRenderState state) {
            TrackingItemStackRenderState itemState = state.itemStackRenderState();
            return !itemState.isAnimated() && itemState.getModelIdentity().equals(modelOnTextureIdentity);
        }
        @Override protected String getTextureLabel() { return "universal_config_profile_icon_"; }
    }
}
