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
import com.android.car.sensitiveapplock.data.AppInfo
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** A data class containing info regarding a lockable app. */
data class LockableApp(val appInfo: AppInfo, val isLocked: Boolean)

/** A data class representing the UI state for the settings screen. */
data class SettingsUiState(
    val appLockEnabled: Boolean = false,
    val appList: List<LockableApp> = emptyList(),
)

/** A [ViewModel] for [SettingsFragment]. */
@HiltViewModel
class SettingsFragmentViewModel
@Inject
constructor(
    private val appLockDataRepository: AppLockDataRepository,
    private val lockableAppsListDataRepository: LockableAppsListRepository,
    private val appSuspensionManager: AppSuspensionManager,
) : ViewModel() {
    val uiState: SharedFlow<SettingsUiState> =
        appLockDataRepository.appLockDataFlow
            .map { appLockData ->
                val lockedAppsSet = appLockData.lockedAppsList.toSet()
                val lockableApps = lockableAppsListDataRepository.getLockableApps()
                SettingsUiState(
                    appLockEnabled = appLockData.password.isNotEmpty(),
                    appList =
                        lockableApps.map { appInfo ->
                            LockableApp(appInfo, lockedAppsSet.contains(appInfo.packageName))
                        },
                )
            }
            .shareIn(viewModelScope, replay = 1, started = SharingStarted.WhileSubscribed(5000L))

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
            // TODO: Use PinManager to clear the stored pin.
        }
    }

    /** Enables or disables the App Lock for an app. */
    fun setAppLockForApp(packageName: String, lock: Boolean) {
        logger.d("Setting lock state for $packageName to $lock.")

        viewModelScope.launch {
            if (lock) {
                if (appLockDataRepository.getLockedApps().contains(packageName)) {
                    logger.w("$packageName is already locked!")
                    return@launch
                }
                appLockDataRepository.addLockedApp(packageName)
            } else {
                appLockDataRepository.removeLockedApp(packageName)
            }

            appSuspensionManager.setAppSuspensionState(packageName, lock)
        }
    }

    private companion object {
        private val logger = Logger(SettingsFragmentViewModel::class.java)
    }
}
