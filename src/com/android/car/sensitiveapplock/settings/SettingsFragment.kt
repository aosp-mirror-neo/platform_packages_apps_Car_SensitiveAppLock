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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.lockscreen.PinLockActivity
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.ui.preference.CarUiFooterPreference
import com.android.car.ui.preference.CarUiSwitchPreference
import com.android.car.ui.preference.PreferenceFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A [PreferenceFragment] that shows the main content for the Settings screen. */
@AndroidEntryPoint(PreferenceFragment::class)
class SettingsFragment : Hilt_SettingsFragment() {
    private val viewModel: SettingsViewModel by viewModels()

    @Inject lateinit var metricsLogger: MetricsLogger

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen =
            preferenceManager.createPreferenceScreen(requireContext()).apply {
                setTitle(R.string.setting_title)

                val appLockSwitch =
                    CarUiSwitchPreference(requireContext()).apply {
                        setTitle(R.string.app_lock_switch)
                        setSummary(R.string.app_lock_switch_summary)
                        key = ENABLE_APP_LOCK_SWITCH_KEY
                        isPersistent = false
                        onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
                            enableAppLockFeature(newValue as Boolean)
                            false // Switch should only change on uiState change
                        }
                    }

                val lockedAppsCategory =
                    PreferenceCategory(requireContext()).apply {
                        setTitle(R.string.protected_apps_category)
                    }

                val legalNotices =
                    CarUiFooterPreference(requireContext()).apply {
                        setSummary(R.string.legal_notice_summary)
                        setIcon(R.drawable.ic_info)
                    }

                addPreference(appLockSwitch)
                addPreference(lockedAppsCategory)
                addPreference(legalNotices)
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> updateUi(state) }
            }
        }
    }

    private fun updateUi(state: SettingsUiState) {
        val appLockSwitch =
            preferenceScreen.getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as CarUiSwitchPreference
        appLockSwitch.isChecked = state.appLockEnabled

        val lockedAppsCategory =
            preferenceScreen.getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
        lockedAppsCategory.removeAll()
        for (lockedApp in state.appList) {
            val switchPreference = createSwitchPreference(lockedApp)
            lockedAppsCategory.addPreference(switchPreference)
            // Dependency set after adding so it can resolve preference_enable_app_lock properly
            switchPreference.dependency = ENABLE_APP_LOCK_SWITCH_KEY
        }
    }

    private fun createSwitchPreference(lockableApp: LockableApp): CarUiSwitchPreference {
        val appSwitchPreference =
            CarUiSwitchPreference(requireContext()).apply {
                key = lockableApp.appInfo.packageName
                title = lockableApp.appInfo.label
                icon = lockableApp.appInfo.icon
                isChecked = lockableApp.isLocked
                isPersistent = false
                onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
                    lifecycleScope.launch {
                        val lockState = newValue as Boolean
                        viewModel.setAppLockForApp(preference.key, lockState)
                        val uid = lockableApp.appInfo.packageUid
                        if (lockState) {
                            metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_ADDED, uid)
                        } else {
                            metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_REMOVED, uid)
                        }
                    }
                    true
                }
            }

        return appSwitchPreference
    }

    private fun enableAppLockFeature(enable: Boolean) {
        if (!enable) {
            viewModel.disableAppLockFeature()
            lifecycleScope.launch { metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_DISABLED) }
            return
        }

        val pinScreenIntent =
            Intent(context, PinLockActivity::class.java).apply {
                action = PinLockActivity.ACTION_CREATE_PIN
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_NO_HISTORY
            }
        startActivity(pinScreenIntent)
    }

    private companion object {
        const val ENABLE_APP_LOCK_SWITCH_KEY = "preference_enable_app_lock"
        const val ENABLE_APP_LOCK_SWITCH_INDEX = 0
        const val LOCKED_APPS_CATEGORY_INDEX = 1
    }
}
