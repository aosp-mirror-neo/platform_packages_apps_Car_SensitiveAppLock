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
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.getAppLockAtoms
import com.android.car.sensitiveapplock.testing.TestHelpers.buildLauncherActivityInfo
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
    @Inject lateinit var pinManager: PinManager

    @Before
    fun init() {
        hiltRule.inject()

        shadowOf(context).grantPermissions("android.permission.SUSPEND_APPS")
        ShadowStatsLog.reset()

        installTestLauncherApps()
    }

    @Test
    fun enableAppLockSwitch_ifUserPinSet_isToggledOn() = runTest {
        pinManager.setAppLockPin(TEST_PIN)

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
        pinManager.setAppLockPin(TEST_PIN)

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
        pinManager.setAppLockPin(TEST_PIN)

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
        pinManager.setAppLockPin(TEST_PIN)
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
    fun appLockFeatureToggledOff_logsMetric() = runTest {
        pinManager.setAppLockPin(TEST_PIN)
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val enableAppLockSwitch =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(ENABLE_APP_LOCK_SWITCH_INDEX) as SwitchPreference

            enableAppLockSwitch.performClick() // Feature turned off
        }

        assertSensitiveAppLockEventAtom(
            statsLogItem = ShadowStatsLog.getStatsLogs().last(),
            appLockEvent = AppLockEvent.APP_LOCK_DISABLED,
        )
    }

    @Test
    fun appLockToggledForApp_logsMetric() = runTest {
        pinManager.setAppLockPin(TEST_PIN)

        launchFragmentInHiltContainer<SettingsFragment> { fragment ->
            val lockedAppsCategory =
                (fragment as SettingsFragment)
                    .preferenceScreen
                    .getPreference(LOCKED_APPS_CATEGORY_INDEX) as PreferenceGroup
            val lockableApp =
                lockedAppsCategory.getPreference(FIRST_LOCKED_APP_INDEX) as SwitchPreference

            lockableApp.performClick()
            lockableApp.performClick()
        }

        val atoms = getAppLockAtoms().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            appLockEvent = AppLockEvent.PACKAGE_REMOVED,
            packageUid = TEST_PACKAGE_UIDS[FIRST_LOCKED_APP_INDEX],
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = true,
            lockedPackages = emptyList(),
            profileLocked = false,
        )
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            appLockEvent = AppLockEvent.PACKAGE_ADDED,
            packageUid = TEST_PACKAGE_UIDS[FIRST_LOCKED_APP_INDEX],
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = true,
            lockedPackages = listOf(TEST_PACKAGE_UIDS[FIRST_LOCKED_APP_INDEX]),
            profileLocked = false,
        )
    }

    private fun installTestLauncherApps() {
        for ((pkg, uid) in TEST_PACKAGE_NAMES.zip(TEST_PACKAGE_UIDS)) {
            shadowLauncherApps.addActivity(
                Process.myUserHandle(),
                buildLauncherActivityInfo(packageName = pkg, uid = uid),
            )
            shadowPackageManager.installPackage(PackageInfo().apply { packageName = pkg })
        }
    }

    private companion object {
        val TEST_PACKAGE_NAMES = listOf("com.package.one", "com.package.two")
        val TEST_PACKAGE_UIDS = listOf(10, 20)
        const val ENABLE_APP_LOCK_SWITCH_INDEX = 0
        const val LOCKED_APPS_CATEGORY_INDEX = 1
        const val FIRST_LOCKED_APP_INDEX = 0
        const val TEST_PIN = "1234"
    }
}
