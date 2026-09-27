/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Ported from com.google.android.systemui.screenshot.surfaceeffects.gloweffect.GlowPieEffectConfig.
 */
package com.google.android.systemui.screenshot.surfaceeffects.gloweffect;

import java.util.Arrays;

/** Immutable-ish configuration for {@link GlowPieEffect}. */
public final class GlowPieEffectConfig {
    public float centerX;
    public float centerY;
    public float width;
    public float height;
    public float cornerRadius;
    /** Exactly three colors: {base, first, second}. */
    public int[] colors;

    @Override
    public String toString() {
        return "GlowPieEffectConfig(centerX=" + centerX + ", centerY=" + centerY
                + ", width=" + width + ", height=" + height + ", cornerRadius=" + cornerRadius
                + ", colors=" + Arrays.toString(colors) + ")";
    }
}
