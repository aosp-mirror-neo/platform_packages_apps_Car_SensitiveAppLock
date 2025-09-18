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

package com.android.car.sensitiveapplock.service

import android.app.Application
import android.car.Car
import android.car.hardware.power.CarPowerManager
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(RobolectricTestParameterInjector::class)
class CarPowerMonitorServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val carPowerManager = mock<CarPowerManager>()
    private val car =
        mock<Car> { on { getCarManager(CarPowerManager::class.java) } doReturn carPowerManager }
    private val powerStateListenerCaptor = argumentCaptor<CarPowerManager.CarPowerStateListener>()

    private lateinit var carPowerMonitorService: CarPowerMonitorService

    @Before
    fun init() {
        hiltRule.inject()

        shadowPackageManager.installPackage(PACKAGE_INFO)
        runTest { appLockDataRepository.addLockedApp(PACKAGE_INFO.packageName) }
        carPowerMonitorService =
            CarPowerMonitorService(
                car,
                appSuspensionManager,
                appLockDataRepository,
                backgroundContext,
            )

        carPowerMonitorService.start()
        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
    }

    @Test
    fun start_locksApps() {
        // Verify apps are locked
        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isTrue()
    }

    @Test
    fun stop_disconnectsCar() {
        carPowerMonitorService.stop()

        verify(car).disconnect()
    }

    @Test
    fun stop_cancelsScope() {
        carPowerMonitorService.stop()
        appSuspensionManager.setAppSuspensionState(PACKAGE_INFO.packageName, false)

        powerStateListenerCaptor.firstValue.onStateChanged(CarPowerManager.STATE_SUSPEND_EXIT)

        // Apps are still unlocked because scope was cancelled
        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isFalse()
    }

    @Test
    fun powerStateListener_onPowerStateChange_updatesAppLockSuspensionStatus(
        @TestParameter(valuesProvider = CarPowerStateValuesProvider::class) state: Int
    ) {
        appSuspensionManager.setAppSuspensionState(PACKAGE_INFO.packageName, false)

        powerStateListenerCaptor.firstValue.onStateChanged(state)

        val pkgSetting = shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName)
        when (state) {
            CarPowerManager.STATE_SHUTDOWN_PREPARE -> assertThat(pkgSetting.isSuspended).isTrue()
            else -> assertThat(pkgSetting.isSuspended).isFalse()
        }
    }

    private object CarPowerStateValuesProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context): List<Int> {
            return listOf(
                CarPowerManager.STATE_HIBERNATION_ENTER,
                CarPowerManager.STATE_HIBERNATION_EXIT,
                CarPowerManager.STATE_INVALID,
                CarPowerManager.STATE_ON,
                CarPowerManager.STATE_POST_HIBERNATION_ENTER,
                CarPowerManager.STATE_POST_SHUTDOWN_ENTER,
                CarPowerManager.STATE_POST_SUSPEND_ENTER,
                CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
                CarPowerManager.STATE_SHUTDOWN_CANCELLED,
                CarPowerManager.STATE_SHUTDOWN_ENTER,
                CarPowerManager.STATE_SHUTDOWN_PREPARE,
                CarPowerManager.STATE_SUSPEND_ENTER,
                CarPowerManager.STATE_SUSPEND_EXIT,
                CarPowerManager.STATE_WAIT_FOR_VHAL,
            )
        }
    }

    private companion object {
        val PACKAGE_INFO = PackageInfo().apply { packageName = "com.test.package" }
    }
}
