/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.sensitiveapplock.lockscreen

import androidx.lifecycle.ViewModel
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.settings.SettingsLockManager
import com.android.car.sensitiveapplock.settings.SettingsLockStatus
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** Viewmodel for the Pin Lock screen. */
@HiltViewModel
class PinLockViewModel
@Inject
constructor(
    private val appLockDataRepository: AppLockDataRepository,
    private val appSuspensionManager: AppSuspensionManager,
    private val pinManager: PinManager,
    private val settingsLockManager: SettingsLockManager,
) : ViewModel() {
    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    /** Temporarily sets the user's pin in memory. */
    fun setEnteredPin(pin: String) {
        _enteredPin.update { currentState -> pin }
    }

    /** Checks if the entered PIN has a valid format. */
    fun doesPinHaveValidFormat(pin: String): Boolean = pinManager.doesPinHaveValidFormat(pin)

    /** Checks if the entered PIN matches the one stored in the database. */
    suspend fun isSavedPin(enteredPin: String): Boolean {
        return pinManager.verifyAppLockPin(enteredPin)
    }

    /**
     * Saves a user's PIN to the datastore.
     *
     * Returns false if the PIN failed to save.
     */
    suspend fun savePin(pin: String): Boolean = pinManager.setAppLockPin(pin)

    /** Unlocks all locked apps. */
    suspend fun unlockApps() {
        val lockedApps = appLockDataRepository.getLockedApps().toTypedArray()
        appSuspensionManager.setAppSuspensionState(packageNames = lockedApps, state = false)
    }

    /**
     * Unlocks the Settings page by setting the Settings Lock Status to
     * [SettingsLockStatus.VALID_PIN].
     */
    fun unlockSettings() = settingsLockManager.setLockStatus(SettingsLockStatus.VALID_PIN)

    /**
     * Sets the Settings Lock Status to [SettingsLockStatus.CANCELED_PIN] if a valid status was not
     * already set.
     */
    suspend fun setCanceledIfNotValid() {
        if (settingsLockManager.lockStatusFlow.first() != SettingsLockStatus.VALID_PIN) {
            settingsLockManager.setLockStatus(SettingsLockStatus.CANCELED_PIN)
        }
    }
}
