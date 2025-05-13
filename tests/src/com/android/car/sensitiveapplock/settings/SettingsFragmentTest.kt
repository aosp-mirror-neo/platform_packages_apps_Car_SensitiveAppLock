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

import android.app.Application
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.os.Process
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreference
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.metrics.SensitiveAppLockStatsLog
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.TestHelpers.buildLauncherActivityInfoFromPackageName
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.shadows.ShadowPackageManager
import org.robolectric.shadows.ShadowStatsLog

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowPackageManager::class, ShadowLauncherApps::class])
class SettingsFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val hiltTestActivityRule = HiltTestActivityRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowPackageManager = shadowOf(context.packageManager)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    @Before
    fun init() {
        hiltRule.inject()

        shadowOf(context).grantPermissions("android.permission.SUSPEND_APPS")
        ShadowStatsLog.reset()

        installTestLauncherApps()
    }

    @Test
    fun enableAppLockSwitch_ifUserPinSet_isToggledOn() = runTest {
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference

            assertThat(enableAppLockSwitch.isChecked).isTrue()
        }
    }

    @Test
    fun enableAppLockSwitch_ifUserPinNotSet_isToggledOff() {
        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference

            assertThat(enableAppLockSwitch.isChecked).isFalse()
        }
    }

    @Test
    fun appLockSwitches_ifEnableAppLockSwitchToggledOff_areDisabled() {
        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp = lockedAppsCategory.getPreference(0) as SwitchPreference

            assertThat(lockableApp.isEnabled).isFalse()
        }
    }

    @Test
    fun appLockSwitches_ifEnableAppLockSwitchToggledOn_areEnabled() = runTest {
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp = lockedAppsCategory.getPreference(0) as SwitchPreference

            assertThat(lockableApp.isEnabled).isTrue()
        }
    }

    @Test
    fun lockedAppsGroup_showsLockableApps() {
        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup

            assertThat(lockedAppsCategory.preferenceCount).isEqualTo(TEST_PACKAGE_NAMES.size)
        }
    }

    @Test
    fun switchToggledOn_suspendsApp() = runTest {
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp = lockedAppsCategory.getPreference(0) as SwitchPreference

            lockableApp.performClick()

            assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[0]).isSuspended)
                .isTrue()
        }
    }

    @Test
    fun switchToggledOff_unsuspendsApp() = runTest {
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp = lockedAppsCategory.getPreference(0) as SwitchPreference

            lockableApp.performClick() // On
            lockableApp.performClick() // Off

            assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[0]).isSuspended)
                .isFalse()
        }
    }

    @Test
    fun appLockFeatureToggledOff_unsuspendsAllApps() = runTest {
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableAppOne = lockedAppsCategory.getPreference(0) as SwitchPreference
            val lockableAppTwo = lockedAppsCategory.getPreference(1) as SwitchPreference

            lockableAppOne.performClick() // Apps locked
            lockableAppTwo.performClick()
            enableAppLockSwitch.performClick() // Feature turned off

            assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[0]).isSuspended)
                .isFalse()
            assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[1]).isSuspended)
                .isFalse()
        }
    }

    @Test
    fun appLockFeatureToggledOff_clearsAppLockData() = runTest {
        appLockDataRepository.setPin(TEST_PIN)
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference

            enableAppLockSwitch.performClick() // Feature turned off
        }

        assertThat(appLockDataRepository.getPin()).isEmpty()
        assertThat(appLockDataRepository.getLockedApps()).isEmpty()
    }

    @Test
    fun appLockFeatureToggled_logsMetric() {
        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference

            enableAppLockSwitch.performClick()
        }

        assertThat(ShadowStatsLog.getStatsLogs().last().atomId())
            .isEqualTo(SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED)
    }

    @Test
    fun appLockToggledForApp_logsMetric() = runTest {
        // Set PIN to enable App Lock feature so app toggles aren't disabled
        appLockDataRepository.setPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp = lockedAppsCategory.getPreference(0) as SwitchPreference

            lockableApp.performClick()
        }

        assertThat(ShadowStatsLog.getStatsLogs().last().atomId())
            .isEqualTo(SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED)
    }

    private fun installTestLauncherApps() {
        for (packageName in TEST_PACKAGE_NAMES) {
            shadowLauncherApps.addActivity(
                Process.myUserHandle(),
                buildLauncherActivityInfoFromPackageName(packageName),
            )
            shadowPackageManager.installPackage(
                PackageInfo().apply { this.packageName = packageName }
            )
        }
    }

    private companion object {
        val TEST_PACKAGE_NAMES = listOf("com.package.one", "com.package.two")
        const val ENABLE_APP_LOCK_SWITCH_INDEX = 0
        const val LOCKED_APPS_CATEGORY_INDEX = 1
        const val TEST_PIN = "1234"
    }
}
