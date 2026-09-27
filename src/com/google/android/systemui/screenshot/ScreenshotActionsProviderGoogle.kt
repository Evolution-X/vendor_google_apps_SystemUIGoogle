/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 Evolution X
 */
package com.google.android.systemui.screenshot

import android.app.Notification
import android.app.assist.AssistContent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.DocumentsContract
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import com.android.internal.logging.UiEventLogger
import com.android.systemui.Flags
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screenshot.ActionExecutor
import com.android.systemui.screenshot.ActionIntentCreator
import com.android.systemui.screenshot.ScreenshotActionsController
import com.android.systemui.screenshot.ScreenshotActionsProvider
import com.android.systemui.screenshot.ScreenshotData
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_COPY_TAPPED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_EDIT_TAPPED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_OPEN_TAPPED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_PREVIEW_TAPPED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_SHARE_TAPPED
import com.android.systemui.screenshot.ScreenshotEvent.SCREENSHOT_SMART_ACTION_TAPPED
import com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider.ScreenshotSmartActionType
import com.android.systemui.screenshot.ScreenshotSavedResult
import com.android.systemui.screenshot.ScrollClickCallback
import com.android.systemui.screenshot.ui.viewmodel.ActionButtonAppearance
import com.android.systemui.screenshot.ui.viewmodel.PreviewAction
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Evolution X screenshot shelf actions: the AOSP default set (preview, share, edit / copy, open
 * in folder, scroll) plus the ASI smart actions stock Pixel SystemUI wires in through
 * [com.android.systemui.screenshot.ScreenshotNotificationSmartActionsProvider]: a quick-share
 * action is requested right away, the regular smart actions once the screenshot has been saved.
 *
 * Port of com.google.android.systemui.screenshot.ScreenshotActionsProviderGoogle (Android 17 /
 * CP2A revision) on top of AOSP [com.android.systemui.screenshot.DefaultScreenshotActionsProvider].
 * Stock differences that were followed: the action buttons carry no label (icon + content
 * description only) and every button, smart action chips included, is added with
 * showDuringEntrance = true.
 *
 * Not ported: the Pixel Screenshots ("Pearl") integration of the stock class (bind to the Pearl
 * action service, hand it the screenshot, stream its suggestion chips, "view in Pixel
 * Screenshots" preview, reminder chip, and the Pearl-availability gate stock pushes into the
 * thumbnail observer for the reveal effects). It depends on the Pixel Screenshots app, which
 * Evolution X does not ship; with Pearl unavailable stock takes exactly the paths implemented
 * here.
 */
class ScreenshotActionsProviderGoogle
@AssistedInject
constructor(
    private val context: Context,
    private val uiEventLogger: UiEventLogger,
    private val actionIntentCreator: ActionIntentCreator,
    private val packageManager: PackageManager,
    private val smartActionsProvider: SmartActionsProvider,
    @Application private val applicationScope: CoroutineScope,
    screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
    @Assisted val requestId: UUID,
    @Assisted val request: ScreenshotData,
    @Assisted val actionExecutor: ActionExecutor,
    @Assisted val actionsCallback: ScreenshotActionsController.ActionsCallback,
) : ScreenshotActionsProvider {
    private var addedScrollChip = false
    private var onScrollClick: ScrollClickCallback? = null
    private var pendingAction: (suspend (ScreenshotSavedResult) -> Unit)? = null
    private var result: ScreenshotSavedResult? = null
    private var webUri: Uri? = null

    private val isCurrentProfile = request.userHandle == Process.myUserHandle()
    private val requestIdString = "Screenshot_$requestId"

    init {
        actionsCallback.providePreviewAction(
            PreviewAction(context.resources.getString(R.string.screenshot_edit_description)) {
                uiEventLogger.log(SCREENSHOT_PREVIEW_TAPPED, 0, request.packageNameString)
                onDeferrableActionTapped { result ->
                    actionExecutor.startSharedTransition(
                        actionIntentCreator.createEdit(result.uri),
                        result.user,
                        true,
                    )
                }
            }
        )

        actionsCallback.provideActionButton(
            ActionButtonAppearance(
                AppCompatResources.getDrawable(context, R.drawable.ic_screenshot_share),
                null,
                context.resources.getString(R.string.screenshot_share_description),
            ),
            showDuringEntrance = true,
        ) {
            uiEventLogger.log(SCREENSHOT_SHARE_TAPPED, 0, request.packageNameString)
            onDeferrableActionTapped { result ->
                val uri = webUri
                val shareIntent =
                    if (uri != null) {
                        actionIntentCreator.createShareWithText(
                            result.uri,
                            extraText = uri.toString(),
                        )
                    } else {
                        actionIntentCreator.createShareWithSubject(result.uri, result.subject)
                    }
                actionExecutor.startSharedTransition(shareIntent, result.user, false)
            }
        }

        if (screenCaptureRecordFeaturesInteractor.isLargeScreenScreencaptureEnabled) {
            actionsCallback.provideActionButton(
                ActionButtonAppearance(
                    AppCompatResources.getDrawable(context, R.drawable.ic_content_copy),
                    null,
                    context.resources.getString(R.string.screenshot_copy_description),
                ),
                showDuringEntrance = true,
            ) {
                uiEventLogger.log(SCREENSHOT_COPY_TAPPED, 0, request.packageNameString)
                onDeferrableActionTapped { result ->
                    actionExecutor.copyScreenshotToClipboard(result.uri)
                }
            }
        } else {
            // The edit button is intentionally hidden on large-screen devices since the screenshot
            // thumbnail serves the same action.
            actionsCallback.provideActionButton(
                ActionButtonAppearance(
                    AppCompatResources.getDrawable(context, R.drawable.ic_screenshot_edit),
                    null,
                    context.resources.getString(R.string.screenshot_edit_description),
                ),
                showDuringEntrance = true,
            ) {
                uiEventLogger.log(SCREENSHOT_EDIT_TAPPED, 0, request.packageNameString)
                onDeferrableActionTapped { result ->
                    actionExecutor.startSharedTransition(
                        actionIntentCreator.createEdit(result.uri),
                        result.user,
                        true,
                    )
                }
            }
        }

        // Check if there is an appropriate package to open up the screenshot's directory before
        // showing the open button.
        if (
            screenCaptureRecordFeaturesInteractor.isLargeScreenScreencaptureEnabled &&
                shouldShowOpenButton()
        ) {
            actionsCallback.provideActionButton(
                ActionButtonAppearance(
                    context.getDrawable(R.drawable.ic_screen_capture_folder),
                    null,
                    context.resources.getString(R.string.screenshot_open_in_folder_description),
                ),
                showDuringEntrance = true,
            ) {
                uiEventLogger.log(SCREENSHOT_OPEN_TAPPED, 0, request.packageNameString)
                onDeferrableActionTapped { result ->
                    val intent = actionIntentCreator.createOpenInFiles(result.uri)
                    if (intent != null) {
                        actionExecutor.startSharedTransition(intent, result.user, false)
                    } else {
                        Log.e(TAG, "Failed to create Intent for mediaStoreUri: ${result.uri} ")
                    }
                }
            }
        }

        // Quick share does not need the saved URI, so ask for it right away (short timeout, the
        // shelf is already animating in).
        if (isCurrentProfile) {
            request.bitmap?.let { bitmap ->
                smartActionsProvider.requestSmartActionsAsync(
                    requestIdString,
                    bitmap,
                    request.topComponent ?: ComponentName("", ""),
                    request.userHandle,
                    null,
                    ScreenshotSmartActionType.QUICK_SHARE_ACTION,
                    QUICK_SHARE_TIMEOUT_MS,
                ) { actions ->
                    actions.firstOrNull()?.let { quickShare -> addQuickShareChip(quickShare) }
                }
            }
        }
    }

    override fun onScrollChipReady(onClick: ScrollClickCallback) {
        onScrollClick = onClick
        if (!addedScrollChip) {
            actionsCallback.provideActionButton(
                ActionButtonAppearance(
                    AppCompatResources.getDrawable(context, R.drawable.ic_screenshot_scroll),
                    null,
                    context.resources.getString(R.string.screenshot_scroll_label),
                ),
                showDuringEntrance = true,
            ) {
                if (Flags.deleteAfterScrollCapture()) {
                    onDeferrableActionTapped { result -> onScrollClick?.invoke(result.uri) }
                } else {
                    onScrollClick?.invoke(Uri.EMPTY)
                }
            }
            addedScrollChip = true
        }
    }

    override fun onScrollChipInvalidated() {
        onScrollClick = null
    }

    override fun setCompletedScreenshot(result: ScreenshotSavedResult) {
        if (this.result != null) {
            Log.e(TAG, "Got a second completed screenshot for existing request!")
            return
        }
        this.result = result
        pendingAction?.also { applicationScope.launch { it.invoke(result) } }

        // The regular smart actions (call, open link, add to wallet, ...) need the saved image.
        if (isCurrentProfile) {
            request.bitmap?.let { bitmap ->
                smartActionsProvider.requestSmartActionsAsync(
                    requestIdString,
                    bitmap,
                    request.topComponent ?: ComponentName("", ""),
                    request.userHandle,
                    result.uri,
                    ScreenshotSmartActionType.REGULAR_SMART_ACTIONS,
                    REGULAR_SMART_ACTIONS_TIMEOUT_MS,
                ) { actions ->
                    actions.forEach { addSmartActionChip(it) }
                }
            }
        }
    }

    override fun onAssistContent(assistContent: AssistContent?) {
        webUri = assistContent?.webUri
    }

    /**
     * Quick share (stock: ScreenshotActionsProviderGoogle quick-share chip): routed through
     * [com.android.systemui.screenshot.SmartActionsReceiver] once the screenshot is saved so the
     * share target receives the image and the tap is reported back to the provider.
     */
    private fun addQuickShareChip(action: Notification.Action) {
        if (action.actionIntent.isImmutable) {
            Log.w(TAG, "Received immutable quick share pending intent; ignoring")
            return
        }
        val title = action.title ?: return
        actionsCallback.provideActionButton(
            ActionButtonAppearance(action.getIcon()?.loadDrawable(context), title, title,
                tint = false),
            showDuringEntrance = true,
        ) {
            uiEventLogger.log(SCREENSHOT_SMART_ACTION_TAPPED, 0, request.packageNameString)
            onDeferrableActionTapped { result ->
                actionExecutor.sendPendingIntent(
                    smartActionsProvider.createSmartActionPendingIntent(
                        action,
                        result,
                        requestIdString,
                    )
                )
            }
        }
    }

    /** Regular smart action (stock: fires the ASI PendingIntent directly). */
    private fun addSmartActionChip(action: Notification.Action) {
        val title = action.title ?: return
        actionsCallback.provideActionButton(
            ActionButtonAppearance(action.getIcon()?.loadDrawable(context), title, title,
                tint = false),
            showDuringEntrance = true,
        ) {
            actionExecutor.sendPendingIntent(action.actionIntent)
        }
    }

    private fun onDeferrableActionTapped(onResult: suspend (ScreenshotSavedResult) -> Unit) {
        result?.let { applicationScope.launch { onResult.invoke(it) } }
            ?: run { pendingAction = onResult }
    }

    private fun shouldShowOpenButton(): Boolean {
        val filesIntent = Intent(Intent.ACTION_VIEW)
        filesIntent.setType(DocumentsContract.Document.MIME_TYPE_DIR)
        filesIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        filesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        filesIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return packageManager.resolveActivity(filesIntent, 0) != null
    }

    @AssistedFactory
    interface Factory : ScreenshotActionsProvider.Factory {
        override fun create(
            requestId: UUID,
            request: ScreenshotData,
            actionExecutor: ActionExecutor,
            actionsCallback: ScreenshotActionsController.ActionsCallback,
        ): ScreenshotActionsProviderGoogle
    }

    companion object {
        private const val TAG = "ScreenshotActionsPrvdr"
        private const val QUICK_SHARE_TIMEOUT_MS = 500L
        private const val REGULAR_SMART_ACTIONS_TIMEOUT_MS = 1000L
    }
}
