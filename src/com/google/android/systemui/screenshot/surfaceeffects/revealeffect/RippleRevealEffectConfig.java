/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Ported from com.google.android.systemui.screenshot.surfaceeffects.revealeffect.RippleRevealEffectConfig.
 */
package com.google.android.systemui.screenshot.surfaceeffects.revealeffect;

/** Configuration for {@link RippleRevealEffect}. Defaults match the stock Pixel reveal. */
public final class RippleRevealEffectConfig {
    /** Total animation duration, in milliseconds. */
    public float duration = 1000f;
    /** When (ms) the inner circle begins fading out. */
    public float innerFadeOutStart = 0f;
    /** When (ms) the outer circle begins fading out. */
    public float outerFadeOutStart = 130f;

    public float centerX;
    public float centerY;
    public float innerRadiusStart;
    public float innerRadiusEnd;
    public float outerRadiusStart;
    public float outerRadiusEnd;
    public float pixelDensity;

    public float blurAmount = 0.5f;
    public int innerColor;
    public int outerColor;
    public float sparkleStrength = 0.3f;
    public float sparkleScale = 0.5f;

    @Override
    public String toString() {
        return "RippleRevealEffectConfig(duration=" + duration + ", innerFadeOutStart="
                + innerFadeOutStart + ", outerFadeOutStart=" + outerFadeOutStart + ", centerX="
                + centerX + ", centerY=" + centerY + ", innerRadiusStart=" + innerRadiusStart
                + ", innerRadiusEnd=" + innerRadiusEnd + ", outerRadiusStart=" + outerRadiusStart
                + ", outerRadiusEnd=" + outerRadiusEnd + ", pixelDensity=" + pixelDensity
                + ", blurAmount=" + blurAmount + ", innerColor=" + innerColor + ", outerColor="
                + outerColor + ", sparkleStrength=" + sparkleStrength + ", sparkleScale="
                + sparkleScale + ")";
    }
}
