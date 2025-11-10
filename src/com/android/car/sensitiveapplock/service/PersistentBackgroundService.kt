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

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.UserManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A persistent background service that starts and stops all registered [AppLockService] instances.
 *
 * This service is started from Car service when the user unlocks the device.
 */
@AndroidEntryPoint(Service::class)
class PersistentBackgroundService : Hilt_PersistentBackgroundService() {
    @Inject lateinit var appLockServices: Set<@JvmSuppressWildcards AppLockService>

    override fun onCreate() {
        super.onCreate()

        logger.v("onCreate")
        if (isGuestUser()) {
            logger.v("Early exit onCreate in guest user")
            return
        }

        for (service in appLockServices) {
            logger.v("Starting service:$service")
            service.start()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = Binder()

    override fun onDestroy() {
        logger.v("onDestroy")
        if (isGuestUser()) {
            logger.v("Early exit onDestroy in guest user")
            super.onDestroy()
            return
        }
        for (service in appLockServices) {
            logger.v("Stopping service:$service")
            service.stop()
        }

        super.onDestroy()
    }

    private fun isGuestUser(): Boolean {
        val userManager = getSystemService(UserManager::class.java)
        return userManager.isGuestUser
    }

    private companion object {
        val logger = Logger(PersistentBackgroundService::class.java)
    }
}
