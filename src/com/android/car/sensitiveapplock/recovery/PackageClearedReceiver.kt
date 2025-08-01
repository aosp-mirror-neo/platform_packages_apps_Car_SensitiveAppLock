/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.sensitiveapplock.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A [BroadcastReceiver] that updates the list of locked system apps whose data has been deleted.
 */
@AndroidEntryPoint(BroadcastReceiver::class)
class PackageClearedReceiver : Hilt_PackageClearedReceiver() {
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var lockableAppsListRepository: LockableAppsListRepository
    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_PACKAGE_DATA_CLEARED) {
            logger.d("Received unexpected intent action:${intent.action}")
            return
        }
        if (UserHandle.SYSTEM == Process.myUserHandle()) {
            logger.v("Skip data clear check for system user")
            return
        }
        val packageName = intent.data?.schemeSpecificPart ?: ""
        if (packageName.isEmpty()) {
            logger.d("Received empty package name")
            return
        }
        // Broadcast receivers are launched on the main thread by default.
        val pendingResult = goAsync()
        CoroutineScope(backgroundContext + SupervisorJob())
            .launch { updateClearedAppsList(packageName) }
            .invokeOnCompletion { pendingResult.finish() }
    }

    private suspend fun updateClearedAppsList(pkg: String) {
        val lockedApps = appLockDataRepository.getLockedApps()
        if (!lockedApps.contains(pkg)) {
            return
        }
        val isSystemApp =
            lockableAppsListRepository.getLockableApps().any {
                it.packageName == pkg && it.isBundledApp
            }
        if (!isSystemApp) {
            return
        }
        appLockDataRepository.addLockedDataClearedSystemApp(pkg)
    }

    private companion object {
        val logger = Logger(PackageClearedReceiver::class.java)
    }
}
