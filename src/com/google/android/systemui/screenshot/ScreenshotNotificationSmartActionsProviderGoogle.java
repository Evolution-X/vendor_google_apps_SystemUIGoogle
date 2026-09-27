/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 *
 * Ported from com.google.android.systemui.screenshot.ScreenshotNotificationSmartActionsProviderGoogle.
 * Supplies smart suggested actions in the screenshot notification (share targets, app / entity
 * actions) by delegating to the ASI (Android System Intelligence) ContentSuggestions service via
 * the matchmaker overview client. Overrides the AOSP base
 * {@link com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider}.
 */
package com.google.android.systemui.screenshot;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;

import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider;

import com.google.android.apps.miphone.aiai.matchmaker.overview.api.generatedv2.FeedbackParcelables_ScreenshotOp;
import com.google.android.apps.miphone.aiai.matchmaker.overview.api.generatedv2.FeedbackParcelables_ScreenshotOpStatus;
import com.google.android.apps.miphone.aiai.matchmaker.overview.api.generatedv2.SuggestParcelables_InteractionType;
import com.google.android.apps.miphone.aiai.matchmaker.overview.ui.ContentSuggestionsServiceClient;
import com.google.android.apps.miphone.aiai.matchmaker.overview.ui.ContentSuggestionsServiceWrapper;
import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Google/ASI-backed implementation of the screenshot notification smart actions provider.
 *
 * <p>Additive: the base screenshot flow is unchanged; when ASI is unavailable or returns nothing,
 * {@link #getActions} completes with an empty list exactly like the AOSP default.
 */
public final class ScreenshotNotificationSmartActionsProviderGoogle
        extends ScreenshotNotificationSmartActionsProvider {

    private static final String TAG = "ScreenshotActionsGoogle";

    /** Bundle key carrying the returned {@link Notification.Action} list from ASI. */
    private static final String KEY_SCREENSHOT_NOTIFICATION_ACTIONS = "ScreenshotNotificationActions";

    private final ContentSuggestionsServiceClient mClient;

    private static final ImmutableMap SCREENSHOT_OP_MAP = ImmutableMap.builder()
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOp.RETRIEVE_SMART_ACTIONS,
                    FeedbackParcelables_ScreenshotOp.RETRIEVE_SMART_ACTIONS)
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOp.REQUEST_SMART_ACTIONS,
                    FeedbackParcelables_ScreenshotOp.REQUEST_SMART_ACTIONS)
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOp.WAIT_FOR_SMART_ACTIONS,
                    FeedbackParcelables_ScreenshotOp.WAIT_FOR_SMART_ACTIONS)
            .build();

    private static final ImmutableMap SCREENSHOT_OP_STATUS_MAP = ImmutableMap.builder()
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus.SUCCESS,
                    FeedbackParcelables_ScreenshotOpStatus.SUCCESS)
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus.ERROR,
                    FeedbackParcelables_ScreenshotOpStatus.ERROR)
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus.TIMEOUT,
                    FeedbackParcelables_ScreenshotOpStatus.TIMEOUT)
            .build();

    private static final ImmutableMap SCREENSHOT_INTERACTION_TYPE_MAP = ImmutableMap.builder()
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotSmartActionType.REGULAR_SMART_ACTIONS,
                    SuggestParcelables_InteractionType.SCREENSHOT_NOTIFICATION)
            .put(ScreenshotNotificationSmartActionsProvider.ScreenshotSmartActionType.QUICK_SHARE_ACTION,
                    SuggestParcelables_InteractionType.QUICK_SHARE)
            .build();

    public ScreenshotNotificationSmartActionsProviderGoogle(
            Context context, Executor executor, Handler handler) {
        mClient = new ContentSuggestionsServiceClient(context, executor, handler);
    }

    @Override
    public CompletableFuture<List<Notification.Action>> getActions(final String screenshotId,
            Uri screenshotUri, Bitmap bitmap, ComponentName componentName,
            ScreenshotNotificationSmartActionsProvider.ScreenshotSmartActionType actionType,
            UserHandle userHandle) {
        final CompletableFuture<List<Notification.Action>> future = new CompletableFuture<>();
        if (bitmap.getConfig() != Bitmap.Config.HARDWARE) {
            Log.e(TAG, String.format(
                    "Bitmap expected: Hardware, Bitmap found: %s. Returning empty list.",
                    bitmap.getConfig()));
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        final long startTimeMs = SystemClock.uptimeMillis();
        Log.d(TAG, "Calling AiAi to obtain screenshot notification smart actions.");
        mClient.provideScreenshotActions(
                bitmap,
                screenshotUri,
                componentName.getPackageName(),
                componentName.getClassName(),
                userHandle,
                (SuggestParcelables_InteractionType) SCREENSHOT_INTERACTION_TYPE_MAP.getOrDefault(
                        actionType, SuggestParcelables_InteractionType.SCREENSHOT_NOTIFICATION),
                new ContentSuggestionsServiceWrapper.BundleCallback() {
                    @Override
                    public void onResult(Bundle bundle) {
                        completeFuture(bundle, future);
                        long durationMs = SystemClock.uptimeMillis() - startTimeMs;
                        Log.d(TAG, String.format(
                                "Total time taken to get smart actions: %d ms", durationMs));
                        notifyOp(screenshotId,
                                ScreenshotNotificationSmartActionsProvider.ScreenshotOp
                                        .RETRIEVE_SMART_ACTIONS,
                                ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus
                                        .SUCCESS,
                                durationMs);
                    }
                });
        return future;
    }

    @Override
    public void notifyOp(String screenshotId,
            ScreenshotNotificationSmartActionsProvider.ScreenshotOp op,
            ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus status, long durationMs) {
        mClient.notifyOp(screenshotId,
                (FeedbackParcelables_ScreenshotOp) SCREENSHOT_OP_MAP.getOrDefault(
                        op, FeedbackParcelables_ScreenshotOp.OP_UNKNOWN),
                (FeedbackParcelables_ScreenshotOpStatus) SCREENSHOT_OP_STATUS_MAP.getOrDefault(
                        status, FeedbackParcelables_ScreenshotOpStatus.OP_STATUS_UNKNOWN),
                durationMs);
    }

    @Override
    public void notifyAction(String screenshotId, String action, boolean isSmartAction,
            Intent intent) {
        mClient.notifyAction(screenshotId, action, isSmartAction, intent);
    }

    void completeFuture(Bundle bundle, CompletableFuture<List<Notification.Action>> future) {
        if (bundle.containsKey(KEY_SCREENSHOT_NOTIFICATION_ACTIONS)) {
            future.complete(bundle.getParcelableArrayList(KEY_SCREENSHOT_NOTIFICATION_ACTIONS));
        } else {
            future.complete(Collections.emptyList());
        }
    }
}
