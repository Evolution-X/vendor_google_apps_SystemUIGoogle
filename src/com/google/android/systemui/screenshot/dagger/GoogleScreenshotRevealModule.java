/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Dagger module for the Evolution X screenshot reveal effects. It provides a
 * ThumbnailObserverGoogle in place of the stock ThumbnailObserver so the effects are wired
 * into the screenshot preview via the AOSP seam (stock: GoogleScreenshotModule#providesThumbnailObserver).
 *
 * IMPORTANT (integration): AOSP's ReferenceScreenshotModule ALREADY has
 *   @Provides static ThumbnailObserver providesThumbnailObserver()
 * and it is pulled into the graph transitively (SystemUIGoogleModule -> ReferenceScreenshotModule
 * -> SysUIGoogleSysUIComponent). Dagger forbids two providers for the same type, so this module and
 * that provider cannot both be present; SystemUIGoogleModule swaps ReferenceScreenshotModule for
 * GoogleScreenshotModule (which includes this module).
 */
package com.google.android.systemui.screenshot.dagger;

import com.google.android.systemui.screenshot.ThumbnailObserverGoogle;
import com.android.systemui.screenshot.ThumbnailObserver;

import dagger.Module;
import dagger.Provides;

@Module
public interface GoogleScreenshotRevealModule {

    /** Provides the Evolution X thumbnail observer that plays the screenshot reveal effects. */
    @Provides
    static ThumbnailObserver provideThumbnailObserverGoogle() {
        return new ThumbnailObserverGoogle();
    }
}
