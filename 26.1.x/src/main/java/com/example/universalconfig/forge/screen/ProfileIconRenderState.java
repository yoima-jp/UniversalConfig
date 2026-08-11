package com.example.universalconfig.forge.screen;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 26.1.2のGUIアイテムアトラスは16px固定のため、16pxを超えるサイズへ拡大すると
 * NEARESTフィルタでピクセルが不均一に割り当てられ、プロフィール一覧のアイコンが荒れる。
 * このレンダーステートはアイテムをターゲット解像度（size*guiScale）のテクスチャへ直接描画し、
 * 16pxアトラス経由の拡大を回避するために{@link ProfileIconRenderer}へ渡す。
 *
 * {@link net.minecraft.client.renderer.state.gui.pip.OversizedItemRenderState}の構造を踏襲するが、
 * バニラはモデルAABBが16pxを超える場合しかこの経路を使えない（フラットな2Dスプライトは除外される）。
 * プロフィールアイコンは通常のブロックモデルでありこの条件を満たさないため、独自ステートで
 * PiP経路へ強制的に乗せる。
 */
public record ProfileIconRenderState(
        Identifier modelId,
        TrackingItemStackRenderState itemStackRenderState,
        int x0,
        int y0,
        int x1,
        int y1,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public ProfileIconRenderState(
            Identifier modelId,
            TrackingItemStackRenderState itemStackRenderState,
            int x0,
            int y0,
            int x1,
            int y1,
            @Nullable ScreenRectangle scissorArea
    ) {
        this(modelId, itemStackRenderState, x0, y0, x1, y1, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @Override
    public float scale() {
        // ItemStackRenderStateはモデル空間の約1単位角としてsubmitされる。バニラの16px
        // OversizedItemRenderStateが16を返すのと同様、ここでは表示したいGUIピクセル数を返す。
        return Math.min(x1 - x0, y1 - y0);
    }
}
