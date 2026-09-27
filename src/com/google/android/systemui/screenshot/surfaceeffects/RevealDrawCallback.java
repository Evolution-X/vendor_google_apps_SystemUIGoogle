/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 */
package com.google.android.systemui.screenshot.surfaceeffects;

import android.graphics.RenderEffect;

/**
 * Per-frame callback carrying the freshly-built shader {@link RenderEffect}. The consumer is
 * responsible for applying it to a view (optionally chaining it with other effects, e.g. blur).
 *
 * <p>Kept local to this feature so the effect classes have zero external dependency; it is the
 * functional equivalent of {@code com.android.systemui.surfaceeffects.RenderEffectDrawCallback}.
 */
public interface RevealDrawCallback {
    void onDraw(RenderEffect renderEffect);
}
