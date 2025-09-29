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

package com.android.car.sensitiveapplock.suspension

import android.car.Car
import android.car.hardware.power.CarPowerManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.service.CarPowerMonitor
import com.android.car.sensitiveapplock.service.PackageChangeMonitor
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(RobolectricTestParameterInjector::class)
class PackageRelockServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var packageChangeMonitor: PackageChangeMonitor
    @Inject lateinit var carPowerMonitor: CarPowerMonitor
    @Inject lateinit var car: Car

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val powerStateListenerCaptor = argumentCaptor<CarPowerManager.CarPowerStateListener>()

    private lateinit var packageRelockService: PackageRelockService
    private lateinit var spyCarPowerMonitor: CarPowerMonitor
    private lateinit var spyPackageChangeMonitor: PackageChangeMonitor
    private lateinit var carPowerManager: CarPowerManager

    @Before
    fun setup() {
        hiltRule.inject()

        spyPackageChangeMonitor = spy(packageChangeMonitor)
        spyCarPowerMonitor = spy(carPowerMonitor)
        carPowerManager = car.getCarManager(CarPowerManager::class.java)!!
        packageRelockService =
            PackageRelockService(
                spyPackageChangeMonitor,
                spyCarPowerMonitor,
                appSuspensionManager,
                appLockDataRepository,
                backgroundContext,
            )
        packageRelockService.start()

        AppInstallationHelper.addAppToPackageManager(context, TEST_PACKAGE_NAME)
    }

    @After
    fun cleanup() {
        packageRelockService.stop()
    }

    @Test
    fun onStart_relocksAllApps() = runTest {
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAME)

        packageRelockService.start()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME).isSuspended).isTrue()
    }

    @Test
    fun onStart_registersListeners() {
        verify(spyPackageChangeMonitor, times(1)).addListener(any())
        verify(spyCarPowerMonitor, times(1)).addListener(any())
    }

    @Test
    fun onStop_unregistersListeners() {
        packageRelockService.stop()

        verify(spyPackageChangeMonitor, times(1)).removeListener(any())
        verify(spyCarPowerMonitor, times(1)).removeListener(any())
    }

    @Test
    fun onReceive_packageAdded_previouslyLocked_relocksPackage() = runTest {
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAME)

        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME).isSuspended).isTrue()
    }

    @Test
    fun onReceive_packageAdded_notPreviouslyLocked_doesNotRelockPackage() {
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME).isSuspended).isFalse()
    }

    @Test
    fun powerStateListener_onPowerStateChange_updatesAppLockSuspensionStatus(
        @TestParameter(valuesProvider = CarPowerStateValuesProvider::class) state: Int
    ) = runTest {
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAME)
        appSuspensionManager.setAppSuspensionState(TEST_PACKAGE_NAME, false)

        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
        powerStateListenerCaptor.firstValue.onStateChanged(state)

        val pkgSetting = shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME)
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
        const val TEST_PACKAGE_NAME = "com.package.ok"

        val BROADCAST_INTENT =
            Intent(Intent.ACTION_PACKAGE_ADDED).setData(Uri.parse("package:$TEST_PACKAGE_NAME"))
    }
}
