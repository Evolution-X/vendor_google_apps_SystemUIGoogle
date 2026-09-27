/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Ported from com.google.android.systemui.screenshot.surfaceeffects.gloweffect.GlowPieEffect
 * (Android 17 / CP2A revision).
 *
 * Draws an animated glowing border made of three overlapping "pie" arcs sweeping around a
 * rounded-box SDF. The per-frame uniform math is reproduced faithfully from the stock animator
 * update listener. Like the stock class (whose wrapper R8 inlined into the screenshot executor)
 * the shader, config and the 0..1 / 3500 ms animator are built once when the effect is created;
 * play() only rewinds the pies and (re)starts the animator, and is a no-op while it is running.
 *
 * Unlike the stock class the three glow definitions are held as instance fields (the stock code
 * keeps them as process-wide static singletons); this keeps concurrent effects independent. The
 * animator update listener is also registered once here instead of on every play().
 */
package com.google.android.systemui.screenshot.surfaceeffects.gloweffect;

import android.animation.ValueAnimator;
import android.graphics.RenderEffect;

import com.google.android.systemui.screenshot.surfaceeffects.RevealDrawCallback;
import com.google.android.systemui.screenshot.surfaceeffects.utils.MathUtils;

public final class GlowPieEffect {

    /** Total animation duration in milliseconds (0xdac in the stock build). */
    private static final long DURATION_MS = 3500L;

    private final GlowPie mBaseGlow = new BaseGlow();
    private final GlowPie mFirstGlowPie = new FirstGlowPie();
    private final GlowPie mSecondGlowPie = new SecondGlowPie();

    private final GlowPieShader mShader;
    private final ValueAnimator mAnimator;
    private final RevealDrawCallback mDrawCallback;

    /** Builds the shader, applies {@code config} and prepares the border-glow animator. */
    public GlowPieEffect(GlowPieEffectConfig config, RevealDrawCallback drawCallback) {
        mDrawCallback = drawCallback;
        mShader = new GlowPieShader();
        mShader.applyConfig(config);

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(DURATION_MS);
        mAnimator.addUpdateListener(this::onAnimationUpdate);
    }

    /** Rewinds the sweeping pies and starts the glow; ignored while the glow is running. */
    public void play() {
        if (mAnimator.isRunning()) {
            return;
        }
        mFirstGlowPie.setProgress(0f);
        mFirstGlowPie.setTime(0f);
        mSecondGlowPie.setProgress(0f);
        mSecondGlowPie.setTime(0f);
        mAnimator.start();
    }

    /** Reapplies a (typically resized) config to the live shader. */
    public void updateConfig(GlowPieEffectConfig config) {
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
        mBaseGlow.setTime(time);
        mFirstGlowPie.updateProgress(time);
        mSecondGlowPie.updateProgress(time);

        mShader.setFloatUniform("in_angles",
                new float[] {0f, mFirstGlowPie.angle(), mSecondGlowPie.angle()});
        mShader.setFloatUniform("in_bottomThresholds", new float[] {
                0f,
                mFirstGlowPie.getProgress() * -1.63f + 1f,
                mSecondGlowPie.getProgress() * -1.63f + 1f});
        mShader.setFloatUniform("in_topThresholds", new float[] {
                0f,
                mFirstGlowPie.getProgress() * -1.63f + 1.63f,
                mSecondGlowPie.getProgress() * -1.63f + 1.63f});
        mShader.setFloatUniform("in_alphas", new float[] {
                mBaseGlow.alpha(), mFirstGlowPie.alpha(), mSecondGlowPie.alpha()});

        mDrawCallback.onDraw(RenderEffect.createRuntimeShaderEffect(mShader, "in_dst"));
    }

    /**
     * A single glowing pie. Timing/geometry are supplied by the concrete implementations; the
     * shared time/angle/alpha math lives in the default methods.
     */
    public interface GlowPie {
        float getStartMs();
        float getEndMs();
        float getStartAngle();
        float getEndAngle();
        float getAlphaFadeStartMs();
        float getAlphaFadeEndMs();
        float getMaxOpacity();
        float getProgress();
        float getTime();
        void setProgress(float progress);
        void setTime(float time);

        /** Opacity for the current time, ramping 0 -> maxOpacity across the alpha-fade window. */
        default float alpha() {
            float fadeStart = getAlphaFadeStartMs();
            float fadeEnd = getAlphaFadeEndMs();
            float amount = (fadeStart == fadeEnd)
                    ? 0f
                    : (getTime() - fadeStart) / (fadeEnd - fadeStart);
            amount = MathUtils.constrain(amount, 0f, 1f);
            return MathUtils.lerp(0f, getMaxOpacity(), amount);
        }

        /** Sweep angle for the current progress (negated to sweep the expected direction). */
        default float angle() {
            float amount = MathUtils.constrain(getProgress(), 0f, 1f);
            return -(getProgress() * (float) Math.PI
                    + MathUtils.lerp(getStartAngle(), getEndAngle(), amount));
        }

        /** Advances progress based on absolute play time, clamped to [startMs, endMs]. */
        default void updateProgress(float timeMs) {
            float startMs = getStartMs();
            float endMs = getEndMs();
            float amount = (startMs == endMs) ? 0f : (timeMs - startMs) / (endMs - startMs);
            amount = MathUtils.constrain(amount, 0f, 1f);
            setProgress(amount);
            setTime(timeMs);
        }
    }

    /** Static base glow drawn underneath; fades in near the end and never sweeps. */
    static final class BaseGlow implements GlowPie {
        private float mProgress = 1f;
        private float mTime;

        @Override public float getStartMs() { return 0f; }
        @Override public float getEndMs() { return 0f; }
        @Override public float getStartAngle() { return 0f; }
        @Override public float getEndAngle() { return 0f; }
        @Override public float getAlphaFadeStartMs() { return 2750f; }
        @Override public float getAlphaFadeEndMs() { return 3450f; }
        @Override public float getMaxOpacity() { return 0.6f; }
        @Override public float getProgress() { return mProgress; }
        @Override public float getTime() { return mTime; }
        @Override public void setProgress(float progress) { mProgress = progress; }
        @Override public void setTime(float time) { mTime = time; }
    }

    /** First sweeping pie. */
    static final class FirstGlowPie implements GlowPie {
        private float mProgress;
        private float mTime;

        @Override public float getStartMs() { return 250f; }
        @Override public float getEndMs() { return 3090f; }
        @Override public float getStartAngle() { return -1.5707964f; }   // -PI/2
        @Override public float getEndAngle() { return 12.566371f; }      // 4*PI
        @Override public float getAlphaFadeStartMs() { return 2840f; }
        @Override public float getAlphaFadeEndMs() { return 3090f; }
        @Override public float getMaxOpacity() { return 0.5f; }
        @Override public float getProgress() { return mProgress; }
        @Override public float getTime() { return mTime; }
        @Override public void setProgress(float progress) { mProgress = progress; }
        @Override public void setTime(float time) { mTime = time; }
    }

    /** Second sweeping pie, drawn on top. */
    static final class SecondGlowPie implements GlowPie {
        private float mProgress;
        private float mTime;

        @Override public float getStartMs() { return 410f; }
        @Override public float getEndMs() { return 3250f; }
        @Override public float getStartAngle() { return -1.5707964f; }   // -PI/2
        @Override public float getEndAngle() { return 9.424778f; }       // 3*PI
        @Override public float getAlphaFadeStartMs() { return 3000f; }
        @Override public float getAlphaFadeEndMs() { return 3250f; }
        @Override public float getMaxOpacity() { return 0.5f; }
        @Override public float getProgress() { return mProgress; }
        @Override public float getTime() { return mTime; }
        @Override public void setProgress(float progress) { mProgress = progress; }
        @Override public void setTime(float time) { mTime = time; }
    }
}
