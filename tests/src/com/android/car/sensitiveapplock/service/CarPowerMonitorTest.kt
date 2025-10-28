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

import android.car.Car
import android.car.hardware.power.CarPowerManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.suspension.AppSuspensionManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CarPowerMonitorTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var carPowerManager: CarPowerManager
    @Inject lateinit var car: Car

    private val powerStateListenerCaptor = argumentCaptor<CarPowerManager.CarPowerStateListener>()

    private lateinit var carPowerMonitorService: CarPowerMonitor

    @Before
    fun init() {
        hiltRule.inject()

        carPowerMonitorService = CarPowerMonitor(car, backgroundContext)
    }

    @Test
    fun addListener_firstListener_setsListenerOnCarPowerManager() {
        val listener = mock<CarPowerMonitor.Listener>()

        carPowerMonitorService.addListener(listener)

        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
        assertThat(powerStateListenerCaptor.firstValue).isNotNull()
    }

    @Test
    fun addListener_multipleListeners_setsListenerOnlyOnce() {
        val listener1 = mock<CarPowerMonitor.Listener>()
        val listener2 = mock<CarPowerMonitor.Listener>()

        carPowerMonitorService.addListener(listener1)
        carPowerMonitorService.addListener(listener2)

        verify(carPowerManager, times(1)).setListener(any(), any())
    }

    @Test
    fun removeListener_lastListener_clearsListenerFromCarPowerManager() {
        val listener = mock<CarPowerMonitor.Listener>()
        carPowerMonitorService.addListener(listener)

        carPowerMonitorService.removeListener(listener)

        verify(carPowerManager).clearListener()
    }

    @Test
    fun removeListener_notLastListener_doesNotClearListener() {
        val listener1 = mock<CarPowerMonitor.Listener>()
        val listener2 = mock<CarPowerMonitor.Listener>()
        carPowerMonitorService.addListener(listener1)
        carPowerMonitorService.addListener(listener2)

        carPowerMonitorService.removeListener(listener1)

        verify(carPowerManager, never()).clearListener()
    }

    @Test
    fun onStateChanged_notifiesRegisteredListeners() {
        val listener1 = mock<CarPowerMonitor.Listener>()
        val listener2 = mock<CarPowerMonitor.Listener>()
        carPowerMonitorService.addListener(listener1)
        carPowerMonitorService.addListener(listener2)

        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
        val capturedListener = powerStateListenerCaptor.firstValue

        capturedListener.onStateChanged(CarPowerManager.STATE_SUSPEND_ENTER)

        verify(listener1).onPowerStateChange(CarPowerManager.STATE_SUSPEND_ENTER)
        verify(listener2).onPowerStateChange(CarPowerManager.STATE_SUSPEND_ENTER)
    }
}
