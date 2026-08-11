package com.example.universalconfig.forge.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * プロフィール一覧のアイコンを16px GUIアトラス経由ではなく、ターゲット解像度のテクスチャへ
 * 直接描画するPiPレンダラー。26.1.2ではアトラスが16px固定のため、28pxなどの非整数倍サイズへ
 * 拡大するとNEARESTでピクセル分布が不均一になりアイコンが荒れる。本レンダラーは
 * {@link net.minecraft.client.gui.render.pip.OversizedItemRenderer}の描画手順を踏襲しつつ、
 * 任意のブロックモデルを指定サイズで高解像度描画できるようにする。
 *
 * バニラのOversizedItemRendererはモデルAABBが16px超のときしか使われないため、フラットな
 * スプライトや通常ブロックモデルはこの経路に入らない。プロフィールアイコンは通常ブロック
 * モデルなので、{@link ProfileIconRenderState}経由で強制的に本レンダラーへ渡す。
 */
public final class ProfileIconRenderer extends PictureInPictureRenderer<ProfileIconRenderState> {
    private final MultiBufferSource.BufferSource iconBufferSource;
    private final Map<RenderTargetKey, IconTextureRenderer> renderersByTarget = new HashMap<>();

    public ProfileIconRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
        this.iconBufferSource = bufferSource;
    }

    @Override
    public Class<ProfileIconRenderState> getRenderStateClass() {
        return ProfileIconRenderState.class;
    }

    @Override
    public void prepare(ProfileIconRenderState state, GuiRenderState guiRenderState, int guiScale) {
        // PictureInPictureRendererは1インスタンスにつきGPUテクスチャを1枚しか持たない。
        // モデル間で共有すると、同一フレームの全blitが最後のアイコンへ上書きされるため、
        // 安定したモデルIDごとに専用レンダラー（専用テクスチャ）を保持する。
        RenderTargetKey key = new RenderTargetKey(state.modelId(), state.x1() - state.x0(), state.y1() - state.y0());
        renderersByTarget.computeIfAbsent(key, ignored -> new IconTextureRenderer(iconBufferSource))
                .prepare(state, guiRenderState, guiScale);
    }

    @Override
    protected void renderToTexture(ProfileIconRenderState state, PoseStack poseStack) {
        throw new IllegalStateException("Profile icon rendering must use a model-specific renderer");
    }

    @Override
    public void close() {
        renderersByTarget.values().forEach(IconTextureRenderer::close);
        renderersByTarget.clear();
        super.close();
    }

    @Override
    protected String getTextureLabel() {
        return "universal_config_profile_icon";
    }

    private record RenderTargetKey(Identifier modelId, int width, int height) {
    }

    private static final class IconTextureRenderer extends PictureInPictureRenderer<ProfileIconRenderState> {
        // 静的アイコンはモデルIDが変わるまで再描画しない。リソース再読み込みでIDが変われば更新する。
        private Object modelOnTextureIdentity;

        private IconTextureRenderer(MultiBufferSource.BufferSource bufferSource) {
            super(bufferSource);
        }

        @Override
        public Class<ProfileIconRenderState> getRenderStateClass() {
            return ProfileIconRenderState.class;
        }

        @Override
        protected void renderToTexture(ProfileIconRenderState state, PoseStack poseStack) {
            // バニラOversizedItemRendererと同じY/Z反転とライティングで、基底クラスが
            // size*guiScaleへ拡大したモデルを同じ大きさのテクスチャへ直接描画する。
            poseStack.scale(1.0F, -1.0F, -1.0F);
            TrackingItemStackRenderState itemStackRenderState = state.itemStackRenderState();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.gameRenderer.getLighting().setupFor(itemStackRenderState.usesBlockLight()
                    ? Lighting.Entry.ITEMS_3D
                    : Lighting.Entry.ITEMS_FLAT);

            FeatureRenderDispatcher dispatcher = minecraft.gameRenderer.getFeatureRenderDispatcher();
            itemStackRenderState.submit(
                    poseStack,
                    dispatcher.getSubmitNodeStorage(),
                    15728880,
                    OverlayTexture.NO_OVERLAY,
                    0);
            dispatcher.renderAllFeatures();
            modelOnTextureIdentity = itemStackRenderState.getModelIdentity();
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected boolean textureIsReadyToBlit(ProfileIconRenderState state) {
            TrackingItemStackRenderState itemStackRenderState = state.itemStackRenderState();
            return !itemStackRenderState.isAnimated()
                    && itemStackRenderState.getModelIdentity().equals(modelOnTextureIdentity);
        }

        @Override
        protected String getTextureLabel() {
            return "universal_config_profile_icon_";
        }
    }
}
