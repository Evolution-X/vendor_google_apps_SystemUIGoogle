/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Ported from com.google.android.systemui.screenshot.surfaceeffects.revealeffect.RippleRevealEffect
 * (Android 17 / CP2A revision).
 *
 * Expands two sparkling, fading circles to reveal content. The per-frame uniform math is
 * reproduced faithfully from the stock animator update listener. Like the stock class (whose
 * wrapper R8 inlined into the screenshot executor) the shader, config and the 0..1 animator are
 * built once when the effect is created; play() sets the duration from the config, starts the
 * animator and reports start/end through the state callback (stock shows the blurred preview on
 * start and hides it again on end so the sharp preview underneath shows through). play() is a
 * no-op while the reveal is running.
 */
package com.google.android.systemui.screenshot.surfaceeffects.revealeffect;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;

import com.google.android.systemui.screenshot.surfaceeffects.RevealDrawCallback;
import com.google.android.systemui.screenshot.surfaceeffects.utils.MathUtils;

public final class RippleRevealEffect {

    /** Start/end notifications of the reveal (stock: the merged "stateChangedCallback"). */
    public interface StateChangedCallback {
        /** The reveal animation has started. */
        void onStart();

        /** The reveal animation has ended. */
        void onEnd();
    }

    /** Scales absolute play time (ms) into the shader's sparkle time domain. */
    private static final float TIME_SCALE = 0.00175f;

    private RippleRevealEffectConfig mConfig;
    private final RippleRevealShader mShader;
    private final ValueAnimator mAnimator;
    private final RevealDrawCallback mRenderEffectCallback;
    private final StateChangedCallback mStateChangedCallback;

    /**
     * Builds the shader, applies {@code config} and prepares the reveal animator.
     *
     * @param renderEffectCallback receives the shader {@link RenderEffect} each frame; the caller
     *                             may chain it (e.g. with a blur) before applying it to a view.
     * @param stateChangedCallback notified when the reveal starts and ends.
     */
    public RippleRevealEffect(RippleRevealEffectConfig config,
            RevealDrawCallback renderEffectCallback, StateChangedCallback stateChangedCallback) {
        mConfig = config;
        mRenderEffectCallback = renderEffectCallback;
        mStateChangedCallback = stateChangedCallback;
        mShader = new RippleRevealShader();
        mShader.applyConfig(config);

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.addUpdateListener(this::onAnimationUpdate);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStateChangedCallback.onEnd();
            }
        });
    }

    /** Starts the reveal; ignored while it is running. */
    public void play() {
        if (mAnimator.isRunning()) {
            return;
        }
        mAnimator.setDuration((long) mConfig.duration);
        mAnimator.start();
        mStateChangedCallback.onStart();
    }

    /** Reapplies a (typically resized) config to the live shader. */
    public void updateConfig(RippleRevealEffectConfig config) {
        mConfig = config;
        mShader.applyConfig(config);
    }

    public boolean isPlaying() {
        return mAnimator.isRunning();
    }

    /** Cancels the animation if running. */
    public void finish() {
        mAnimator.cancel();
    }

    private void onAnimationUpdate(ValueAnimator anim) {
        float time = anim.getCurrentPlayTime();
        mShader.setFloatUniform("in_time", TIME_SCALE * time);

        float fraction = (float) anim.getAnimatedValue();
        float innerRadius =
                MathUtils.lerp(mConfig.innerRadiusStart, mConfig.innerRadiusEnd, fraction);
        float outerRadius =
                MathUtils.lerp(mConfig.outerRadiusStart, mConfig.outerRadiusEnd, fraction);
        mShader.setFloatUniform("in_innerRadius", innerRadius);
        mShader.setFloatUniform("in_outerRadius", outerRadius);

        float innerFade = MathUtils.constrainedMap(
                1f, 0f, mConfig.innerFadeOutStart, mConfig.duration, time);
        float outerFade = MathUtils.constrainedMap(
                1f, 0f, mConfig.outerFadeOutStart, mConfig.duration, time);
        mShader.setColorUniform(
                "in_innerColor", withAlpha(mConfig.innerColor, (int) (255f * innerFade)));
        mShader.setColorUniform(
                "in_outerColor", withAlpha(mConfig.outerColor, (int) (255f * outerFade)));
        mShader.setFloatUniform("in_dstAlpha", Math.max(innerFade, outerFade));
        mShader.setFloatUniform("in_sparkleAlpha", Math.min(innerFade, outerFade));

        mRenderEffectCallback.onDraw(RenderEffect.createRuntimeShaderEffect(mShader, "in_dst"));
    }

    /** Replaces the alpha channel of {@code color} with {@code alpha} (0..255). Self-contained. */
    private static int withAlpha(int color, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
