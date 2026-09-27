/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 The Evolution X Project
 */
package com.google.android.systemui.controls

import android.content.ComponentName
import com.android.systemui.controls.controller.ControlsTileResourceConfiguration
import com.android.systemui.controls.dagger.ControlsComponent
import com.android.systemui.dagger.SysUISingleton
import com.google.android.systemui.res.R
import javax.inject.Inject
import com.android.systemui.res.R as AospR

/** Package name of the Google Home app, as a Device Controls provider. */
private const val GOOGLE_HOME_PACKAGE_NAME = "com.google.android.apps.chromecast.app"

/**
 * Swaps the Quick Settings "Device controls" tile's icon/label for Google Home branding when
 * Google Home is the user's currently-preferred controls provider; otherwise falls back to the
 * generic AOSP title/icon.
 *
 * [ControlsComponent.getControlsController] returns `Optional.empty()` until the controls feature
 * has actually loaded a binding, and [SelectedItem.componentName] is only meaningful once the user
 * has a saved selection -- both cases fall through to the generic AOSP resources below, same as
 * [ControlsTileResourceConfigurationImpl] would.
 */
@SysUISingleton
class GoogleControlsTileResourceConfigurationImpl
@Inject
constructor(private val controlsComponent: ControlsComponent) : ControlsTileResourceConfiguration {

    private fun preferredPackageName(): String? {
        if (!controlsComponent.isEnabled()) return null
        val controller = controlsComponent.getControlsController().orElse(null) ?: return null
        val componentName: ComponentName = controller.getPreferredSelection().componentName
        return componentName.packageName
    }

    private val isGoogleHomeSelected: Boolean
        get() = preferredPackageName() == GOOGLE_HOME_PACKAGE_NAME

    override fun getPackageName(): String? =
        if (isGoogleHomeSelected) GOOGLE_HOME_PACKAGE_NAME else null

    override fun getTileImageId(): Int =
        if (isGoogleHomeSelected) R.drawable.home_controls_icon else AospR.drawable.controls_icon

    override fun getTileTitleId(): Int =
        if (isGoogleHomeSelected) R.string.home_controls_tile_title
        else AospR.string.quick_controls_title
}
