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
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.suspension.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * An [AppLockService] for relocking user apps.
 *
 * This service locks user apps when:
 * * The device is entering suspend to ram, hibernation or shutdown state.
 * * The user has unlocked the device.
 */
class CarPowerMonitorService
@Inject
constructor(
    private val car: Car?,
    private val appSuspensionManager: AppSuspensionManager,
    private val appLockDataRepository: AppLockDataRepository,
    @BackgroundContext backgroundContext: CoroutineContext,
) : AppLockService {
    private val executor = (backgroundContext as CoroutineDispatcher).asExecutor()
    private val scope = CoroutineScope(backgroundContext)
    private val powerStateListener =
        CarPowerManager.CarPowerStateListener { powerState ->
            logger.v("power state changed:$powerState")
            if (powerState == CarPowerManager.STATE_SHUTDOWN_PREPARE) {
                lockApps()
            }
        }

    override fun start() {
        logger.v("starting car power monitor service")
        car?.getCarManager(CarPowerManager::class.java)?.setListener(executor, powerStateListener)

        // Service is started when the user unlocks the device.
        lockApps()
    }

    override fun stop() {
        car?.disconnect()
        scope.cancel()
        logger.v("stopping car power monitor service")
    }

    private fun lockApps() =
        scope.launch {
            val appList = appLockDataRepository.getLockedApps().toTypedArray()

            logger.v("Locking user apps on boot:${appList.contentToString()}")

            appSuspensionManager.setAppSuspensionState(appList, state = true)
        }

    private companion object {
        val logger = Logger(CarPowerMonitorService::class.java)
    }
}
