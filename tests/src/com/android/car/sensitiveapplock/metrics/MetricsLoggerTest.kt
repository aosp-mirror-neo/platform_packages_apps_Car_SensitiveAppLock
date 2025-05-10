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
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.experimental.and
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
import org.robolectric.shadows.ShadowStatsLog.StatsLogItem

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
        atoms.last().assertSensitiveAppLockAtom(
            pinSet = false,
            lockedPackages = emptyList(),
            profileLocked = false
        )
    }

    @Test
    fun logState_whenAppsLocked_producesAtomWithPackageUids() = runTest {
        addLauncherActivities()
        appLockDataRepository.setPin(USER_PIN)
        shadowKeyguardManager.setIsDeviceSecure(true)

        metricsLogger.logState()

        val atoms = ShadowStatsLog.getStatsLogs()
        atoms.last().assertSensitiveAppLockAtom(
            pinSet = true,
            lockedPackages = TEST_ACTIVITIES_UID,
            profileLocked = true
        )
    }

    private fun StatsLogItem.assertSensitiveAppLockAtom(
        pinSet: Boolean,
        lockedPackages: List<Int>,
        profileLocked: Boolean
    ) {
        assertThat(atomId()).isEqualTo(SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED)
        val atomBytes = getByteBuffer(this)

        // skip header
        atomBytes.position(HEADER)

        // pin set
        var field = parseFieldMetaData(atomBytes)
        assertThat(field.type).isEqualTo(TYPE_BOOLEAN)
        assertThat(atomBytes.get()).isEqualTo(pinSet.toByte())
        skipAnnotations(atomBytes, field.annotationCount)

        // locked packages
        field = parseFieldMetaData(atomBytes)
        assertThat(field.type).isEqualTo(TYPE_LIST)
        assertThat(atomBytes.get()).isEqualTo(lockedPackages.size.toByte())
        assertThat(atomBytes.get()).isEqualTo(TYPE_INT) // Element type
        for (pkg in lockedPackages) {
            assertThat(atomBytes.getInt()).isEqualTo(pkg)
        }
        skipAnnotations(atomBytes, field.annotationCount)

        // profile lock
        field = parseFieldMetaData(atomBytes)
        assertThat(field.type).isEqualTo(TYPE_BOOLEAN)
        assertThat(atomBytes.get()).isEqualTo(profileLocked.toByte())
        skipAnnotations(atomBytes, field.annotationCount)
    }

    private fun skipAnnotations(buffer: ByteBuffer, annotationCount: Int) {
        repeat(annotationCount) {
            buffer.get() // read annotation id
            val annotationType = buffer.get()
            when (annotationType) {
                TYPE_INT -> buffer.getInt()
                TYPE_BOOLEAN -> buffer.get()
            }
        }
    }

    private fun parseFieldMetaData(buffer: ByteBuffer): FieldMetaData {
        val typeWithAnnotation = buffer.get()
        val annotationCount = typeWithAnnotation.toInt() shr 4
        return FieldMetaData(
            type = typeWithAnnotation and MASK,
            annotationCount
        )
    }

    private fun getByteBuffer(item: StatsLogItem): ByteBuffer {
        return ByteBuffer.wrap(item.bytes()).order(ByteOrder.LITTLE_ENDIAN)
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

    private fun Boolean.toByte(): Byte {
        if (this) {
            return 1.toByte()
        }
        return 0.toByte()
    }

    private data class FieldMetaData(
        val type: Byte,
        val annotationCount: Int
    )

    private companion object {
        // This is borrowed from android.util.StatsEvent
        const val TYPE_INT: Byte = 0x00
        const val TYPE_LIST: Byte = 0x03
        const val TYPE_BOOLEAN: Byte = 0x05
        const val MASK: Byte = 0x0F
        const val HEADER = 16
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
