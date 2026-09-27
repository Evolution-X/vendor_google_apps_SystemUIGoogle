/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Evolution X screenshot-preview reveal effects. Extends the AOSP ThumbnailObserver seam
 * (com.android.systemui.screenshot.ThumbnailObserver), which ScreenshotShelfViewProxy drives via
 * setViews()/onEntranceStarted()/onEntranceComplete(). Mirrors the stock Pixel
 * ThumbnailObserverGoogle (Android 17 / CP2A revision), but self-contained: the effects live
 * entirely in this module.
 *
 * Stock behaviour reproduced here:
 *  - setViews() builds both effects up front (the image is the blurred copy of the preview,
 *    R.id.screenshot_preview_blur, which the shelf layout keeps invisible) and registers layout
 *    listeners that re-apply the geometry when the views are (re)laid out and tint the border
 *    background with the tertiary-fixed-dim color.
 *  - Nothing plays on onEntranceStarted(); both effects start in onEntranceComplete(). The reveal
 *    shows the blurred preview while it runs and hides it again when done; the glow keeps
 *    sweeping the border for ~3.5 s.
 *  - Colors: materialColorTertiaryFixedDim + material grey 900 (stock reads the latter from the
 *    appcompat resource; the value is inlined here).
 *
 * Stock gates the effects on Pixel Screenshots ("Pearl") availability, which the Pixel
 * ScreenshotActionsProviderGoogle pushes into the observer per screenshot. Evolution X has no
 * Pearl and the effects are the feature, so they are enabled unconditionally.
 *
 * Additive by design: if the views are missing it silently no-ops, leaving the stock screenshot
 * flow untouched.
 */
package com.google.android.systemui.screenshot;

import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import com.google.android.systemui.screenshot.surfaceeffects.gloweffect.GlowPieEffect;
import com.google.android.systemui.screenshot.surfaceeffects.gloweffect.GlowPieEffectConfig;
import com.google.android.systemui.screenshot.surfaceeffects.revealeffect.RippleRevealEffect;
import com.google.android.systemui.screenshot.surfaceeffects.revealeffect.RippleRevealEffectConfig;
import com.android.systemui.screenshot.ThumbnailObserver;

public final class ThumbnailObserverGoogle extends ThumbnailObserver {

    private static final String TAG = "ThumbnailObserver";

    /** Corner radius (dp) of the glowing border box. */
    private static final float BORDER_CORNER_RADIUS_DP = 32f;
    /** Blur radius applied to the preview image, in dp. */
    private static final float PREVIEW_BLUR_DP = 20f;
    /** appcompat {@code material_grey_900}, the dark color stock pairs with the tertiary tint. */
    private static final int MATERIAL_GREY_900 = 0xFF212121;

    private GlowPieEffect mGlowBorderEffect;
    private RippleRevealEffect mRippleRevealEffect;

    /**
     * Stock: {@code pearlEnabled}, set per screenshot by the Pixel actions provider. Always on
     * here (see the class comment).
     */
    private final boolean mEffectsEnabled = true;

    private final View.OnLayoutChangeListener mThumbnailLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!mEffectsEnabled || mRippleRevealEffect == null) {
                    return;
                }
                mRippleRevealEffect.updateConfig(createRippleRevealConfig(v));
            };

    private final View.OnLayoutChangeListener mBorderLayoutChangeListener =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (!mEffectsEnabled) {
                    return;
                }
                if (mGlowBorderEffect != null) {
                    mGlowBorderEffect.updateConfig(createGlowBorderConfig(v));
                }
                final Drawable background = v.getBackground();
                if (background != null) {
                    background.setTint(tertiaryFixedDimColor(v.getContext()));
                }
            };

    public ThumbnailObserverGoogle() {}

    @Override
    public void setViews(ImageView image, View border) {
        if (image == null || border == null) {
            return;
        }

        // Ripple reveal over a blurred copy of the preview. The blurred ImageView is invisible in
        // the shelf layout; it is shown while the reveal plays and hidden again when it finishes.
        final float blur = image.getResources().getDisplayMetrics().density * PREVIEW_BLUR_DP;
        final RenderEffect blurEffect =
                RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP);
        mRippleRevealEffect = new RippleRevealEffect(
                createRippleRevealConfig(image),
                renderEffect -> image.setRenderEffect(
                        RenderEffect.createChainEffect(renderEffect, blurEffect)),
                new RippleRevealEffect.StateChangedCallback() {
                    @Override
                    public void onStart() {
                        image.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onEnd() {
                        image.setVisibility(View.INVISIBLE);
                    }
                });
        image.addOnLayoutChangeListener(mThumbnailLayoutChangeListener);

        // Glowing animated border around the preview.
        mGlowBorderEffect = new GlowPieEffect(createGlowBorderConfig(border),
                border::setRenderEffect);
        border.addOnLayoutChangeListener(mBorderLayoutChangeListener);
    }

    @Override
    public void onEntranceStarted() {
        Log.d(TAG, "Entrance started");
    }

    @Override
    public void onEntranceComplete() {
        Log.d(TAG, "Entrance complete");
        if (!mEffectsEnabled) {
            return;
        }
        // Both effects ignore play() while they are already running.
        if (mRippleRevealEffect != null) {
            mRippleRevealEffect.play();
        }
        if (mGlowBorderEffect != null) {
            mGlowBorderEffect.play();
        }
    }

    private static int tertiaryFixedDimColor(Context context) {
        return context.getColor(com.android.internal.R.color.materialColorTertiaryFixedDim);
    }

    private static GlowPieEffectConfig createGlowBorderConfig(View view) {
        final Context ctx = view.getContext();
        final float width = view.getWidth();
        final float height = view.getHeight();
        final int tertiaryFixedDim = tertiaryFixedDimColor(ctx);
        final float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, BORDER_CORNER_RADIUS_DP,
                ctx.getResources().getDisplayMetrics());

        GlowPieEffectConfig config = new GlowPieEffectConfig();
        config.centerX = width * 0.5f;
        config.centerY = height * 0.5f;
        config.width = width;
        config.height = height;
        config.cornerRadius = cornerRadius;
        // {base, first, second}
        config.colors = new int[] {tertiaryFixedDim, MATERIAL_GREY_900, tertiaryFixedDim};
        return config;
    }

    private static RippleRevealEffectConfig createRippleRevealConfig(View view) {
        final Context ctx = view.getContext();
        final float max = Math.max(view.getWidth(), view.getHeight()) * 3.0f;
        final float radiusStart = 0.05f * max;

        RippleRevealEffectConfig config = new RippleRevealEffectConfig();
        config.centerX = view.getWidth() * 0.5f;
        config.centerY = view.getHeight();
        config.innerRadiusStart = radiusStart;
        config.innerRadiusEnd = max;
        config.outerRadiusStart = radiusStart * 1.25f;
        config.outerRadiusEnd = max * 1.25f;
        config.pixelDensity = ctx.getResources().getDisplayMetrics().density;
        config.innerColor = tertiaryFixedDimColor(ctx);
        config.outerColor = MATERIAL_GREY_900;
        return config;
    }
}
