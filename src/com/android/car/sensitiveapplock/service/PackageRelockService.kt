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

import android.car.hardware.power.CarPowerManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.suspension.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * An [AppLockService] for relocking user apps.
 *
 * This service relocks user apps when:
 * - The device is entering suspend to ram, hibernation or shutdown state.
 * - The user has unlocked the device.
 * - The user has reinstalled a locked app.
 */
class PackageRelockService
@Inject
constructor(
    private val packageChangeMonitor: PackageChangeMonitor,
    private val carPowerMonitor: CarPowerMonitor,
    private val appSuspensionManager: AppSuspensionManager,
    private val appLockDataRepository: AppLockDataRepository,
    @BackgroundContext backgroundContext: CoroutineContext,
) : AppLockService {
    private val backgroundScope = CoroutineScope(backgroundContext)

    private val packageChangeListener =
        object : PackageChangeMonitor.Listener {
            override fun onPackageAdded(packageName: String) {
                backgroundScope.launch { relockAppIfPreviouslyLocked(packageName) }
            }
        }

    private val carPowerMonitorListener =
        object : CarPowerMonitor.Listener {
            override fun onPowerStateChange(powerState: Int) {
                if (powerState == CarPowerManager.STATE_SHUTDOWN_PREPARE) {
                    backgroundScope.launch { lockAllApps() }
                }
            }
        }

    override fun start() {
        logger.v("Starting AppReinstallMonitorService.")
        packageChangeMonitor.addListener(packageChangeListener)
        carPowerMonitor.addListener(carPowerMonitorListener)

        // Redundancy in case the BootReceiver does not relock apps
        backgroundScope.launch { lockAllApps() }
    }

    override fun stop() {
        logger.v("Stopping AppReinstallMonitorService.")
        packageChangeMonitor.removeListener(packageChangeListener)
        carPowerMonitor.removeListener(carPowerMonitorListener)
        backgroundScope.cancel()
    }

    private suspend fun relockAppIfPreviouslyLocked(packageName: String) {
        if (appLockDataRepository.getLockedApps().contains(packageName)) {
            logger.v("Relocking $packageName.")
            appSuspensionManager.setAppSuspensionState(packageName, state = true)
        }
    }

    private suspend fun lockAllApps() {
        val appList = appLockDataRepository.getLockedApps().toTypedArray()

        logger.v("Locking user apps on boot:${appList.contentToString()}")

        appSuspensionManager.setAppSuspensionState(appList, state = true)
    }

    private companion object {
        val logger = Logger(PackageRelockService::class.java)
    }
}
