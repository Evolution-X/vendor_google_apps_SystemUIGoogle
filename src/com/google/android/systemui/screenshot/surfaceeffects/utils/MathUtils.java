/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Small self-contained math helpers for the screenshot surface effects. Mirrors the
 * semantics of com.android.systemui.surfaceeffects.utils.MathUtils / the stock
 * com.google.android.systemui.screenshot.surfaceeffects.utils.MathUtils.
 */
package com.google.android.systemui.screenshot.surfaceeffects.utils;

public final class MathUtils {

    private MathUtils() {}

    /** Linear interpolation between {@code start} and {@code stop} by {@code amount}. */
    public static float lerp(float start, float stop, float amount) {
        return start + (stop - start) * amount;
    }

    /** Clamps {@code amount} to the inclusive range [{@code low}, {@code high}]. */
    public static float constrain(float amount, float low, float high) {
        return amount < low ? low : (amount > high ? high : amount);
    }

    /**
     * Maps {@code value} from the input range [{@code inStart}, {@code inEnd}] to the output
     * range [{@code outStart}, {@code outEnd}], clamping the normalized amount to [0, 1].
     */
    public static float constrainedMap(
            float outStart, float outEnd, float inStart, float inEnd, float value) {
        float amount = (inEnd == inStart)
                ? 0f
                : constrain((value - inStart) / (inEnd - inStart), 0f, 1f);
        return lerp(outStart, outEnd, amount);
    }
}
