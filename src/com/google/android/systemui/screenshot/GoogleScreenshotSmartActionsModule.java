/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 */
package com.google.android.systemui.screenshot;

import android.content.Context;
import android.os.Handler;
import android.provider.DeviceConfig;

import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;

import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

/**
 * Dagger seam for Evolution X screenshot notification smart actions.
 *
 * <p>Overrides the AOSP base binding of {@link ScreenshotNotificationSmartActionsProvider}
 * (declared in {@code com.android.systemui.screenshot.ReferenceScreenshotModule}) with the
 * ASI-backed {@link ScreenshotNotificationSmartActionsProviderGoogle}, gated on the same
 * DeviceConfig flag Google uses so the AOSP default is returned when disabled.
 *
 * <p>This module binds ONLY the notification smart actions provider. The sibling screenshot
 * overlay feature owns the {@code ScreenshotActionsProvider.Factory} and {@code ThumbnailObserver}
 * overrides. Because all three bindings live in the single precompiled
 * {@code ReferenceScreenshotModule}, this module cannot simply be ADDED alongside it (that is a
 * Dagger duplicate binding); it must REPLACE it in the component graph. See the feature's
 * integration spec for the exact module-graph change.
 */
@Module
public interface GoogleScreenshotSmartActionsModule {

    /** DeviceConfig namespace / flag mirroring the stock Pixel gate. */
    String NAMESPACE_SYSTEMUI = "systemui";
    String FLAG_ENABLE_SCREENSHOT_NOTIFICATION_SMART_ACTIONS =
            "enable_screenshot_notification_smart_actions";

    /**
     * Replacement for
     * {@code ReferenceScreenshotModule#providesScrnshtNotifSmartActionsProvider()}.
     */
    @Provides
    static ScreenshotNotificationSmartActionsProvider providesScrnshtNotifSmartActionsProvider(
            Context context, @Background Executor bgExecutor, @Main Handler mainHandler) {
        if (!DeviceConfig.getBoolean(NAMESPACE_SYSTEMUI,
                FLAG_ENABLE_SCREENSHOT_NOTIFICATION_SMART_ACTIONS, true)) {
            return new ScreenshotNotificationSmartActionsProvider();
        }
        return new ScreenshotNotificationSmartActionsProviderGoogle(
                context, bgExecutor, mainHandler);
    }
}
