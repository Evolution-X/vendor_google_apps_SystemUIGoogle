/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (C) 2026 FundamentalOS
 */
package com.google.android.systemui.keyguard.refreshrate

import com.android.systemui.biometrics.AuthController
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.data.repository.BiometricSettingsRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Computes when SystemUI should ask the display for an elevated (max) refresh rate ahead of a
 * biometric unlock, so that the fingerprint / face unlock animations render smoothly.
 *
 * The request is active only while a fingerprint is enrolled and enabled AND the device is in a
 * pre-auth state -- the lockscreen is visible or the alternate (UDFPS) bouncer is showing. When no
 * fingerprint is usable there is nothing to accelerate, so the flow collapses to a constant
 * `false`.
 *
 * Ported from com.google.android.systemui.keyguard.domain.interactor.RefreshRateInteractor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class RefreshRateInteractor
@Inject
constructor(
    biometricSettingsRepository: BiometricSettingsRepository,
    keyguardInteractor: KeyguardInteractor,
    private val alternateBouncerInteractor: AlternateBouncerInteractor,
    val authController: AuthController,
) {
    val requestOverridingMaxRefreshRate: Flow<Boolean> =
        biometricSettingsRepository.isFingerprintEnrolledAndEnabled.flatMapLatest { enrolled ->
            if (enrolled) {
                combine(
                    keyguardInteractor.isKeyguardVisible,
                    alternateBouncerInteractor.isVisible,
                ) { keyguardVisible, alternateBouncerVisible ->
                    keyguardVisible || alternateBouncerVisible
                }
            } else {
                flowOf(false)
            }
        }
}
