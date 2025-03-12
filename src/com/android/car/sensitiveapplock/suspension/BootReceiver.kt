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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A [BroadcastReceiver] that relocks user apps when the device boots or when the user switches
 * back to their profile.
 */
@AndroidEntryPoint(BroadcastReceiver::class)
class BootReceiver : Hilt_BootReceiver() {
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Broadcast receivers are launched on the main thread by default.
        val pendingResult = goAsync()
        CoroutineScope(backgroundContext + SupervisorJob()).launch {
            lockApps()
        }.invokeOnCompletion {
            pendingResult.finish()
        }
    }

    private suspend fun lockApps() {
        val appList = appLockDataRepository.getLockedApps().toTypedArray()

        logger.v("Locking user apps on boot:${appList.contentToString()}")

        appSuspensionManager.setAppSuspensionState(appList, state = true)
    }

    private companion object {
        val logger = Logger(BootReceiver::class.java)
    }
}
