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

import android.app.Application
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
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
import org.robolectric.shadows.ShadowPackageManager

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowPackageManager::class])
class PinLockViewModelTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    lateinit var pinLockViewModel: PinLockViewModel

    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var appSuspensionManager: AppSuspensionManager

    @Inject lateinit var pinManager: PinManager

    @Before
    fun init() {
        hiltRule.inject()
        pinLockViewModel = PinLockViewModel(appLockDataRepository, appSuspensionManager, pinManager)
    }

    @Test
    fun setEnteredPin_setsEnteredPin() {
        pinLockViewModel.setEnteredPin(USER_PIN)

        assertThat(pinLockViewModel.enteredPin.value).isEqualTo(USER_PIN)
    }

    @Test
    fun isSavedPin_ifCorrectPin_returnsTrue() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        assertThat(pinLockViewModel.isSavedPin(USER_PIN)).isTrue()
    }

    @Test
    fun isSavedPin_ifIncorrectPin_returnsFalse() = runTest {
        assertThat(pinLockViewModel.isSavedPin(USER_PIN)).isFalse()
    }

    @Test
    fun savePin_savesPinToDb() = runTest {
        pinLockViewModel.savePin(USER_PIN)

        assertThat(pinManager.verifyAppLockPin(USER_PIN)).isTrue()
    }

    @Test
    fun unlockApps_unsuspendsAllApps() = runTest {
        val shadowPackageManager = shadowOf(context.packageManager)
        for (packageName in TEST_PACKAGE_NAMES) {
            shadowPackageManager.installPackage(
                PackageInfo().apply { this.packageName = packageName }
            )
            appSuspensionManager.setAppSuspensionState(packageName, true)
            appLockDataRepository.addLockedApp(packageName)
        }

        pinLockViewModel.unlockApps()

        for (packageName in TEST_PACKAGE_NAMES) {
            assertThat(shadowPackageManager.getPackageSetting(packageName).isSuspended).isFalse()
        }
    }

    private companion object {
        const val USER_PIN = "1111"

        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")
    }
}
