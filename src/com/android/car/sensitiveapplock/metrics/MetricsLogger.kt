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
import com.android.car.sensitiveapplock.auth.PinManager
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
    private val pinManager: PinManager,
    @BackgroundContext private val backgroundContext: CoroutineContext,
) {
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** Logs the user's current app lock state. */
    suspend fun logState() =
        withContext(backgroundContext) {
            val lockedApps = appLockDataRepository.getLockedApps().toHashSet()
            val packageUids =
                lockableAppsListRepository
                    .getLockableApps()
                    .filter { lockedApps.contains(it.packageName) }
                    .map { it.packageUid }
            val pinSet = pinManager.getAppLockPinState() == PinManager.PinState.SET
            val profileLockSet = keyguardManager.isDeviceSecure
            SensitiveAppLockStatsLog.write(
                SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED,
                pinSet,
                packageUids.toIntArray(),
                profileLockSet,
            )
            // TODO: b/416017684 - Add ATS test for logging metrics.
        }

    /**
     * Logs an app lock event.
     *
     * Pass the [packageUid] when logging a package related events like
     * [AppLockEvent.PACKAGED_ADDED], [AppLockEvent.PACKAGE_REMOVED] or similar events.
     */
    suspend fun logAppLockEvent(event: AppLockEvent, packageUid: Int = NULL_PACKAGE_UID) =
        withContext(backgroundContext) {
            when (event) {
                AppLockEvent.APP_LOCK_ENABLED,
                AppLockEvent.APP_LOCK_DISABLED,
                AppLockEvent.PACKAGED_ADDED,
                AppLockEvent.PACKAGE_REMOVED -> logState()
                else -> {}
            }
            SensitiveAppLockStatsLog.write(
                SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED,
                packageUid,
                event.value,
                SignInEvent.UNSPECIFIED.value,
                RecoveryEvent.UNSPECIFIED.value,
            )
        }

    /** Logs sign-in related flow events. */
    suspend fun logSignInEvent(event: SignInEvent) =
        withContext(backgroundContext) {
            SensitiveAppLockStatsLog.write(
                SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED,
                NULL_PACKAGE_UID,
                AppLockEvent.UNSPECIFIED.value,
                event.value,
                RecoveryEvent.UNSPECIFIED.value,
            )
        }

    /** Logs recovery flow events. */
    suspend fun logRecoveryEvent(event: RecoveryEvent) =
        withContext(backgroundContext) {
            SensitiveAppLockStatsLog.write(
                SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED,
                NULL_PACKAGE_UID,
                AppLockEvent.UNSPECIFIED.value,
                SignInEvent.UNSPECIFIED.value,
                event.value,
            )
        }

    private companion object {
        const val NULL_PACKAGE_UID = 0
    }
}

/** App lock events that can be logged. */
enum class AppLockEvent(val value: Int) {
    UNSPECIFIED(0),
    APP_LOCK_ENABLED(1),
    APP_LOCK_DISABLED(2),
    APP_LOCK_SETTINGS_SCREEN_OPENED(3),
    PACKAGED_ADDED(4),
    PACKAGE_REMOVED(5),
    PACKAGE_UNLOCK_REQUESTED(6),
    PACKAGE_LAUNCHED(7),
}

/** Sign-in events that can be logged. */
enum class SignInEvent(val value: Int) {
    UNSPECIFIED(0),
    USER_ALREADY_SIGNED_IN(1),
    USER_STARTED_SIGN_IN(2),
    USER_COMPLETED_SIGN_IN(3),
    USER_DECLINED_SIGN_IN(4),
}

/** Recovery flow events that can be logged. */
enum class RecoveryEvent(val value: Int) {
    UNSPECIFIED(0),
    USER_STARTED_REAUTH_RECOVERY_FLOW(1),
    USER_COMPLETED_REAUTH_RECOVERY_FLOW(2),
    USER_STARTED_MANUAL_RESET_RECOVERY_FLOW(3),
    USER_STARTED_PIN_RECREATE_FLOW(4),
    USER_RECREATED_PIN(5),
}
