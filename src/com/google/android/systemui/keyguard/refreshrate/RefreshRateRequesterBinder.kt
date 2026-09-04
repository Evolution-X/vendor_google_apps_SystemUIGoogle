/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.google.android.systemui.keyguard.refreshrate

import android.content.res.Resources
import android.os.RemoteException
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.google.android.systemui.res.R
import dagger.Lazy
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * [CoreStartable] that forwards [RefreshRateInteractor.requestOverridingMaxRefreshRate] to
 * [AuthController.requestMaxRefreshRate], asking the display for an elevated refresh rate while the
 * lockscreen is up so biometric unlock feels snappier.
 *
 * Passive: it only observes and forwards. All gating (fingerprint enrolled, keyguard/bouncer
 * visibility) lives in the interactor, and the whole feature is disabled unless
 * `config_request_pre_auth_refresh_rate` is set. [AuthController.requestMaxRefreshRate] itself
 * no-ops (and logs) when no display callback is registered.
 *
 * Ported from com.google.android.systemui.keyguard.RefreshRateRequesterBinder.
 */
@SysUISingleton
class RefreshRateRequesterBinder
@Inject
constructor(
    @Main private val resources: Resources,
    private val interactor: Lazy<RefreshRateInteractor>,
    @Application private val scope: CoroutineScope,
) : CoreStartable {

    override fun start() {
        if (!resources.getBoolean(R.bool.config_request_pre_auth_refresh_rate)) {
            return
        }
        interactor
            .get()
            .requestOverridingMaxRefreshRate
            .onEach { request -> requestMaxRefreshRate(request) }
            .launchIn(scope)
    }

    private fun requestMaxRefreshRate(request: Boolean) {
        try {
            interactor.get().authController.requestMaxRefreshRate(request)
        } catch (e: RemoteException) {
            // The refresh-rate callback is owned by the display service; if that binder has died
            // there is nothing actionable here -- the next state change re-issues the request.
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("enabled: " + resources.getBoolean(R.bool.config_request_pre_auth_refresh_rate))
    }
}
