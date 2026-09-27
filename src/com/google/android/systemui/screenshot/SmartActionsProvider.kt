/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 */
package com.google.android.systemui.screenshot

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.provider.DeviceConfig
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider.ScreenshotOp
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider.ScreenshotOpStatus
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider.ScreenshotSmartActionType
import com.android.systemui.screenshot.ScreenshotSavedResult
import com.android.systemui.screenshot.SmartActionsReceiver
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges the screenshot shelf UI to the [ScreenshotNotificationSmartActionsProvider] (ASI on
 * Evolution X).
 *
 * AOSP still ships the provider abstraction, but since the shelf UI replaced the notification
 * actions nothing in AOSP asks it for actions any more. Stock Pixel SystemUI does this from its
 * own ScreenshotActionsProviderGoogle through
 * com.google.android.systemui.screenshot.SmartActionsProvider; this class is the port of that
 * request/dispatch half (Android 17 / CP2A revision).
 */
@SysUISingleton
class SmartActionsProvider
@Inject
constructor(
    private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    smartActionsProviderProvider: Provider<ScreenshotNotificationSmartActionsProvider>,
) {
    private val smartActions: ScreenshotNotificationSmartActionsProvider by lazy {
        smartActionsProviderProvider.get()
    }

    /**
     * Asks the provider for smart actions of [actionType] and hands the (possibly empty) result to
     * [onActions] on the application scope. Never throws; failures and timeouts yield an empty
     * list and are reported to the provider through notifyOp.
     */
    fun requestSmartActionsAsync(
        screenshotId: String,
        image: Bitmap,
        component: ComponentName,
        user: UserHandle,
        uri: Uri?,
        actionType: ScreenshotSmartActionType,
        timeoutMs: Long,
        onActions: (List<Notification.Action>) -> Unit,
    ) {
        applicationScope.launch {
            val actions =
                withContext(bgDispatcher) {
                    requestSmartActions(screenshotId, image, component, user, uri, actionType,
                        timeoutMs)
                }
            onActions(actions)
        }
    }

    private fun requestSmartActions(
        screenshotId: String,
        image: Bitmap,
        component: ComponentName,
        user: UserHandle,
        uri: Uri?,
        actionType: ScreenshotSmartActionType,
        timeoutMs: Long,
    ): List<Notification.Action> {
        val enabled =
            user == Process.myUserHandle() &&
                DeviceConfig.getBoolean(NAMESPACE_SYSTEMUI, FLAG_SMART_ACTIONS, true)
        if (!enabled) {
            Log.d(TAG, "Smart actions disabled for $actionType, returning empty list")
            return emptyList()
        }
        if (image.config != Bitmap.Config.HARDWARE) {
            Log.d(TAG, "Bitmap expected: Hardware, found: ${image.config}; returning empty list")
            return emptyList()
        }
        val startTimeMs = SystemClock.uptimeMillis()
        val future =
            try {
                smartActions.getActions(screenshotId, uri, image, component, actionType, user)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to request $actionType smart actions", e)
                notifyScreenshotOp(screenshotId, ScreenshotOp.REQUEST_SMART_ACTIONS,
                    ScreenshotOpStatus.ERROR, SystemClock.uptimeMillis() - startTimeMs)
                return emptyList()
            }
        return try {
            val actions = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            val waitTimeMs = SystemClock.uptimeMillis() - startTimeMs
            Log.d(TAG, "Got ${actions.size} $actionType smart actions in $waitTimeMs ms")
            notifyScreenshotOp(screenshotId, ScreenshotOp.WAIT_FOR_SMART_ACTIONS,
                ScreenshotOpStatus.SUCCESS, waitTimeMs)
            actions
        } catch (e: Throwable) {
            val waitTimeMs = SystemClock.uptimeMillis() - startTimeMs
            val status =
                if (e is TimeoutException) ScreenshotOpStatus.TIMEOUT else ScreenshotOpStatus.ERROR
            Log.w(TAG, "Error waiting for $actionType smart actions ($status after $waitTimeMs ms)")
            notifyScreenshotOp(screenshotId, ScreenshotOp.WAIT_FOR_SMART_ACTIONS, status,
                waitTimeMs)
            emptyList()
        }
    }

    /**
     * Wraps a smart [action] so it is dispatched through [SmartActionsReceiver], which fills in the
     * saved screenshot and reports the tap back to the provider, exactly like the notification
     * actions used to. Stock only routes the quick-share action this way; the regular smart
     * actions fire their own PendingIntent directly.
     */
    fun createSmartActionPendingIntent(
        action: Notification.Action,
        result: ScreenshotSavedResult,
        screenshotId: String,
    ): PendingIntent {
        val fillIn =
            Intent().apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, result.uri)
                putExtra(Intent.EXTRA_SUBJECT, result.subject)
                clipData =
                    ClipData(
                        ClipDescription("content", arrayOf("image/png")),
                        ClipData.Item(result.uri),
                    )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val actionType =
            action.extras.getString(
                ScreenshotNotificationSmartActionsProvider.ACTION_TYPE,
                ScreenshotNotificationSmartActionsProvider.DEFAULT_ACTION_TYPE,
            )
        val intent =
            Intent(context, SmartActionsReceiver::class.java).apply {
                putExtra(SmartActionsReceiver.EXTRA_ACTION_INTENT, action.actionIntent)
                putExtra(SmartActionsReceiver.EXTRA_ACTION_INTENT_FILLIN, fillIn)
                putExtra(SmartActionsReceiver.EXTRA_ACTION_TYPE, actionType)
                putExtra(SmartActionsReceiver.EXTRA_ID, screenshotId)
                putExtra(SmartActionsReceiver.EXTRA_SMART_ACTIONS_ENABLED, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return PendingIntent.getBroadcast(
            context,
            Random.nextInt(),
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notifyScreenshotOp(
        screenshotId: String,
        op: ScreenshotOp,
        status: ScreenshotOpStatus,
        durationMs: Long,
    ) {
        try {
            smartActions.notifyOp(screenshotId, op, status, durationMs)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in notifyScreenshotOp", e)
        }
    }

    companion object {
        private const val TAG = "SmartActionsProvider"
        private const val NAMESPACE_SYSTEMUI = "systemui"
        private const val FLAG_SMART_ACTIONS = "enable_screenshot_notification_smart_actions"
    }
}
