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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.testing.TestHelpers.buildLauncherActivityInfoFromPackageName
import com.android.car.sensitiveapplock.util.AppSuspensionManager
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
import org.robolectric.shadows.ShadowContextWrapper
import org.robolectric.shadows.ShadowLauncherApps
import org.robolectric.shadows.ShadowPackageManager

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(
    shadows = [ShadowPackageManager::class, ShadowLauncherApps::class, ShadowContextWrapper::class]
)
class SettingsViewModelTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowPackageManager = shadowOf(context.packageManager)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var lockableAppsListRepository: LockableAppsListRepository
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var settingsLockManager: SettingsLockManager
    @Inject lateinit var pinManager: PinManager

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun init() {
        shadowOf(context).grantPermissions("android.permission.SUSPEND_APPS")
        hiltRule.inject()

        // Can't inject a [HiltViewModel]
        viewModel =
            SettingsViewModel(
                appLockDataRepository,
                lockableAppsListRepository,
                appSuspensionManager,
                settingsLockManager,
                pinManager
            )

        installTestLauncherApps()
    }

    @Test
    fun disabledAppLockFeature_unlocksAllLockedApps() {
        for (packageName in TEST_PACKAGE_NAMES) {
            viewModel.setAppLockForApp(packageName, true)
        }

        viewModel.disableAppLockFeature()

        for (packageName in TEST_PACKAGE_NAMES) {
            assertThat(shadowPackageManager.getPackageSetting(packageName).isSuspended).isFalse()
        }
    }

    @Test
    fun disabledAppLockFeature_clearsData() = runTest {
        for (packageName in TEST_PACKAGE_NAMES) {
            viewModel.setAppLockForApp(packageName, true)
        }

        viewModel.disableAppLockFeature()

        assertThat(appLockDataRepository.getLockedApps()).isEmpty()
        assertThat(appLockDataRepository.getPin()).isEmpty()
    }

    @Test
    fun setAppLockForApp_lockTrue_suspendsApp() {
        val packageName = TEST_PACKAGE_NAMES[0]
        viewModel.setAppLockForApp(packageName, true)

        assertThat(shadowPackageManager.getPackageSetting(packageName).isSuspended).isTrue()
    }

    @Test
    fun setAppLockForApp_lockFalse_unsuspendsApp() {
        val packageName = TEST_PACKAGE_NAMES[0]
        viewModel.setAppLockForApp(packageName, true)
        viewModel.setAppLockForApp(packageName, false)

        assertThat(shadowPackageManager.getPackageSetting(packageName).isSuspended).isFalse()
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
        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")
    }
}
