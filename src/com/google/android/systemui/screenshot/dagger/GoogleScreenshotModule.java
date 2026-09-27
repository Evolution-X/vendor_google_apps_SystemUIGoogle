/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 */
package com.google.android.systemui.screenshot.dagger;

import com.google.android.systemui.screenshot.ScreenshotActionsProviderGoogle;
import com.google.android.systemui.screenshot.GoogleScreenshotSmartActionsModule;
import com.android.systemui.screenshot.ScreenshotActionsProvider;

import dagger.Binds;
import dagger.Module;

/**
 * Evolution X replacement for {@code com.android.systemui.screenshot.ReferenceScreenshotModule}
 * (mirrors stock Pixel's own {@code GoogleScreenshotModule}).
 *
 * <p>The base {@code ReferenceScreenshotModule} provides three things: a {@code ThumbnailObserver},
 * a {@code ScreenshotNotificationSmartActionsProvider}, and a {@code @Binds} for
 * {@code ScreenshotActionsProvider.Factory}. Evolution X overrides all three:
 * <ul>
 *   <li>{@link GoogleScreenshotRevealModule} supplies the glow/ripple
 *       {@code ThumbnailObserverGoogle} (screenshot reveal effects).</li>
 *   <li>{@link GoogleScreenshotSmartActionsModule} supplies the ASI-backed
 *       {@code ScreenshotNotificationSmartActionsProviderGoogle}.</li>
 *   <li>The {@link ScreenshotActionsProvider.Factory} points at
 *       {@link ScreenshotActionsProviderGoogle}, which adds the ASI quick-share / smart
 *       action chips on top of the AOSP default shelf actions. Nothing in AOSP requests smart
 *       actions any more, so without this override the smart actions provider is never
 *       called.</li>
 * </ul>
 * This module is swapped in for {@code ReferenceScreenshotModule} inside {@link
 * com.google.android.systemui.dagger.SystemUIGoogleModule} so the net graph contains exactly
 * one binding for each of the three keys.
 */
@Module(includes = {
        GoogleScreenshotSmartActionsModule.class,
        GoogleScreenshotRevealModule.class,
})
public interface GoogleScreenshotModule {

    /** */
    @Binds
    ScreenshotActionsProvider.Factory bindScreenshotActionsProviderFactory(
            ScreenshotActionsProviderGoogle.Factory screenshotActionsProviderFactory);
}
