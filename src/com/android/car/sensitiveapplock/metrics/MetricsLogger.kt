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
package com.android.car.sensitiveapplock.metrics

import android.app.KeyguardManager
import android.content.Context
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Helper class for logging metric events. */
@Singleton
class MetricsLogger
@Inject
constructor(
    @ApplicationContext context: Context,
    private val appLockDataRepository: AppLockDataRepository,
    private val lockableAppsListRepository: LockableAppsListRepository,
    @BackgroundContext private val backgroundContext: CoroutineContext
) {
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** Logs the user's current app lock state. */
    suspend fun logState() = withContext(backgroundContext) {
        val lockedApps = appLockDataRepository.getLockedApps().toHashSet()
        val packageUids = lockableAppsListRepository.getLockableApps().filter {
            lockedApps.contains(it.packageName)
        }.map { it.packageUid }
        val pinSet = appLockDataRepository.getPin().isNotEmpty()
        val profileLockSet = keyguardManager.isDeviceSecure
        SensitiveAppLockStatsLog.write(
            SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED,
            pinSet,
            packageUids.toIntArray(),
            profileLockSet
        )
        // TODO: b/416017684 - Add ATS test for logging metrics.
    }
}
