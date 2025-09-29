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
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor

/** Monitors for car power state changes and notifies registered listeners. */
@Singleton
open class CarPowerMonitor
@Inject
constructor(private val car: Car?, @BackgroundContext backgroundContext: CoroutineContext) {
    private val listeners = mutableSetOf<Listener>()
    private val executor = (backgroundContext as CoroutineDispatcher).asExecutor()

    private val powerStateListener =
        object : CarPowerManager.CarPowerStateListener {
            @Synchronized
            override fun onStateChanged(powerState: Int) {
                logger.v("power state changed:$powerState")
                for (listener in listeners) {
                    listener.onPowerStateChange(powerState)
                }
            }
        }

    /** Adds a [Listener] to be notified of car power state changes. */
    @Synchronized
    open fun addListener(listener: Listener) {
        val wasEmpty = listeners.isEmpty()
        if (listeners.add(listener) && wasEmpty) {
            registerCarPowerListener()
        }
    }

    /** Removes a [Listener] from being notified of car power state changes. */
    @Synchronized
    open fun removeListener(listener: Listener) {
        if (listeners.remove(listener) && listeners.isEmpty()) {
            unregisterCarPowerListener()
        }
    }

    private fun registerCarPowerListener() {
        car?.getCarManager(CarPowerManager::class.java)?.setListener(executor, powerStateListener)
    }

    private fun unregisterCarPowerListener() {
        car?.getCarManager(CarPowerManager::class.java)?.clearListener()
    }

    /** Interface for listeners interested in power state changes. */
    interface Listener {
        /** Called when the car power state changes. */
        fun onPowerStateChange(powerState: Int)
    }

    private companion object {
        val logger = Logger(CarPowerMonitor::class.java)
    }
}
