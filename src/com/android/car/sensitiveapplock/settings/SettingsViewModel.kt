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
package com.android.car.sensitiveapplock.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppInfo
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** A data class containing info regarding a lockable app. */
data class LockableApp(val appInfo: AppInfo, val isLocked: Boolean)

/** A data class representing the UI state for the Settings screen. */
data class SettingsUiState(
    val settingsLockStatus: SettingsLockStatus = SettingsLockStatus.UNSET,
    val appLockEnabled: Boolean = false,
    val appList: List<LockableApp> = emptyList(),
)

/** A [ViewModel] for the Settings screen. */
@HiltViewModel
class SettingsViewModel
@Inject
constructor(
    private val appLockDataRepository: AppLockDataRepository,
    private val lockableAppsListDataRepository: LockableAppsListRepository,
    private val appSuspensionManager: AppSuspensionManager,
    private val settingsLockManager: SettingsLockManager,
    private val pinManager: PinManager,
) : ViewModel() {
    val uiState: SharedFlow<SettingsUiState> =
        combine(appLockDataRepository.appLockDataFlow, settingsLockManager.lockStatusFlow) {
                appLockData,
                lockStatus ->
                val lockedAppsSet = appLockData.lockedAppsList.toSet()
                val lockableApps = lockableAppsListDataRepository.getLockableApps()
                SettingsUiState(
                    settingsLockStatus = lockStatus,
                    appLockEnabled = pinManager.getAppLockPinState() == PinManager.PinState.SET,
                    appList =
                        lockableApps.map { appInfo ->
                            LockableApp(appInfo, lockedAppsSet.contains(appInfo.packageName))
                        },
                )
            }
            .shareIn(
                viewModelScope,
                replay = 1,
                started = SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS),
            )

    /**
     * Disables the App Lock feature.
     *
     * This will unlock any locked apps and clear the App Lock user data.
     */
    fun disableAppLockFeature() {
        viewModelScope.launch {
            val lockedApps = appLockDataRepository.getLockedApps().toTypedArray()
            appSuspensionManager.setAppSuspensionState(packageNames = lockedApps, state = false)
            appLockDataRepository.clearData()
            pinManager.clearAppLockPin()
        }
    }

    /** Enables or disables the App Lock for an app. */
    suspend fun setAppLockForApp(packageName: String, lock: Boolean) {
        logger.d("Setting lock state for $packageName to $lock.")

        if (lock) {
            if (appLockDataRepository.getLockedApps().contains(packageName)) {
                logger.w("$packageName is already locked!")
                return
            }
            appLockDataRepository.addLockedApp(packageName)
        } else {
            appLockDataRepository.removeLockedApp(packageName)
        }

        appSuspensionManager.setAppSuspensionState(packageName, lock)
    }

    /** Checks if the user has a PIN set. */
    suspend fun isPinSet(): Boolean {
        return pinManager.getAppLockPinState() == PinManager.PinState.SET
    }

    /** Unlocks the Settings screen. */
    fun unlockSettings() = settingsLockManager.setLockStatus(SettingsLockStatus.VALID_PIN)

    /** Locks the Settings screen. */
    fun lockSettings() = settingsLockManager.setLockStatus(SettingsLockStatus.UNSET)

    private companion object {
        val logger = Logger(SettingsViewModel::class.java)

        val FLOW_STOP_TIMEOUT_MILLIS = 5000L
    }
}
