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

import android.Manifest.permission.SUSPEND_APPS
import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.ApplicationInfoBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowKeyguardManager
import org.robolectric.shadows.ShadowStatsLog

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MetricsLoggerTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowKeyguardManager =
        shadowOf(context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var pinManager: PinManager

    @Inject lateinit var metricsLogger: MetricsLogger

    @Before
    fun init() {
        hiltRule.inject()
        shadowOf(context).grantPermissions(SUSPEND_APPS)
        ShadowStatsLog.reset()
        ShadowKeyguardManager.reset()
    }

    @Test
    fun logState_producesAtom() = runTest {
        metricsLogger.logState()

        val atoms = ShadowStatsLog.getStatsLogs()
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.last(),
            pinSet = false,
            lockedPackages = emptyList(),
            profileLocked = false,
        )
    }

    @Test
    fun logState_whenAppsLocked_producesAtomWithPackageUids() = runTest {
        addLauncherActivities()
        pinManager.setAppLockPin(USER_PIN)
        shadowKeyguardManager.setIsDeviceSecure(true)

        metricsLogger.logState()

        val atoms = ShadowStatsLog.getStatsLogs()
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.last(),
            pinSet = true,
            lockedPackages = TEST_ACTIVITIES_UID,
            profileLocked = true,
        )
    }

    @Test
    fun logAppLockEvent_onAppLockEnabled_producesEventAndStateAtom() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_ENABLED)

        val atoms = ShadowStatsLog.getStatsLogs().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            appLockEvent = AppLockEvent.APP_LOCK_ENABLED,
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = true,
            lockedPackages = emptyList(),
            profileLocked = false,
        )
    }

    @Test
    fun logAppLockEvent_onAppLockDisabled_producesEventAndStateAtom() = runTest {
        pinManager.setAppLockPin(USER_PIN)
        pinManager.clearAppLockPin()

        metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_DISABLED)

        val atoms = ShadowStatsLog.getStatsLogs().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            appLockEvent = AppLockEvent.APP_LOCK_DISABLED,
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = false,
            lockedPackages = emptyList(),
            profileLocked = false,
        )
    }

    @Test
    fun logAppLockEvent_onPackageAdded_producesEventAndStateAtom() = runTest {
        addLauncherActivities()
        pinManager.setAppLockPin(USER_PIN)
        shadowKeyguardManager.setIsDeviceSecure(true)

        metricsLogger.logAppLockEvent(AppLockEvent.PACKAGED_ADDED, TEST_ACTIVITIES_UID.last())

        val atoms = ShadowStatsLog.getStatsLogs().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            packageUid = TEST_ACTIVITIES_UID.last(),
            appLockEvent = AppLockEvent.PACKAGED_ADDED,
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = true,
            lockedPackages = TEST_ACTIVITIES_UID,
            profileLocked = true,
        )
    }

    @Test
    fun logAppLockEvent_onPackageRemoved_producesEventAndStateAtom() = runTest {
        addLauncherActivities()
        appLockDataRepository.removeLockedApp(TEST_ACTIVITIES.last())
        pinManager.setAppLockPin(USER_PIN)

        metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_REMOVED, TEST_ACTIVITIES_UID.last())

        val atoms = ShadowStatsLog.getStatsLogs().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            packageUid = TEST_ACTIVITIES_UID.last(),
            appLockEvent = AppLockEvent.PACKAGE_REMOVED,
        )
        assertSensitiveAppLockAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            pinSet = true,
            lockedPackages = listOf(TEST_ACTIVITIES_UID.first()),
            profileLocked = false,
        )
    }

    @Test
    fun logAppLockEvent_producesEventAtom() = runTest {
        metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_LAUNCHED)

        val atoms = ShadowStatsLog.getStatsLogs()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            appLockEvent = AppLockEvent.PACKAGE_LAUNCHED,
        )
    }

    @Test
    fun logSignInEvent_producesEventAtom() = runTest {
        metricsLogger.logSignInEvent(SignInEvent.USER_COMPLETED_SIGN_IN)

        val atoms = ShadowStatsLog.getStatsLogs()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            signInEvent = SignInEvent.USER_COMPLETED_SIGN_IN,
        )
    }

    @Test
    fun logRecoveryEvent_producesEventAtom() = runTest {
        metricsLogger.logRecoveryEvent(RecoveryEvent.USER_RECREATED_PIN)

        val atoms = ShadowStatsLog.getStatsLogs()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            recoveryEvent = RecoveryEvent.USER_RECREATED_PIN,
        )
    }

    private suspend fun addLauncherActivities() {
        for ((pkg, uid) in TEST_ACTIVITIES.zip(TEST_ACTIVITIES_UID)) {
            shadowLauncherApps.addActivity(
                Process.myUserHandle(),
                buildLauncherActivityInfo(pkg, uid),
            )
            appLockDataRepository.addLockedApp(pkg)
        }
    }

    private companion object {
        const val USER_PIN = "1234"

        val TEST_ACTIVITIES = listOf("com.package.1", "com.package.2")
        val TEST_ACTIVITIES_UID = listOf(10, 20)

        fun buildLauncherActivityInfo(packageName: String, uid: Int): LauncherActivityInfo {
            val applicationInfo =
                ApplicationInfoBuilder.newBuilder().setPackageName(packageName).build()
            applicationInfo.uid = uid
            val mockLauncherActivityInfo =
                mock<LauncherActivityInfo>().apply {
                    whenever(getApplicationInfo()).thenReturn(applicationInfo)
                }
            return mockLauncherActivityInfo
        }
    }
}
