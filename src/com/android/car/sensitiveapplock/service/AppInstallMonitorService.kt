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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.suspension.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** An [AppLockService] for relocking apps that have been reinstalled. */
class AppInstallMonitorService
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appSuspensionManager: AppSuspensionManager,
    private val appLockDataRepository: AppLockDataRepository,
    @BackgroundContext backgroundContext: CoroutineContext,
) : AppLockService, BroadcastReceiver() {
    private val backgroundScope = CoroutineScope(backgroundContext)

    override fun start() {
        logger.v("Starting AppReinstallMonitorService.")

        val intentFilter =
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        context.registerReceiver(this, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    override fun stop() {
        logger.v("Stopping AppReinstallMonitorService.")
        context.unregisterReceiver(this)
        backgroundScope.cancel()
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        logger.v("Received PACKAGE_ADDED broadcast!")

        val packageName = intent?.data?.schemeSpecificPart
        if (packageName == null) {
            logger.w("Received null package name.")
            return
        }
        logger.v("$packageName was installed!")

        backgroundScope.launch { relockAppIfPreviouslyLocked(packageName) }
    }

    private suspend fun relockAppIfPreviouslyLocked(packageName: String) {
        if (appLockDataRepository.getLockedApps().contains(packageName)) {
            logger.v("Relocking $packageName.")
            appSuspensionManager.setAppSuspensionState(packageName, state = true)
        }
    }

    private companion object {
        val logger = Logger(AppInstallMonitorService::class.java)
    }
}
