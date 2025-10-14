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
package com.android.car.sensitiveapplock.testing

import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.RecoveryEvent
import com.android.car.sensitiveapplock.metrics.SensitiveAppLockStatsLog
import com.android.car.sensitiveapplock.metrics.SignInEvent
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import org.robolectric.shadows.ShadowStatsLog
import org.robolectric.shadows.ShadowStatsLog.StatsLogItem

/** Object containing helper methods for testing Metrics. */
object MetricsTestHelper {

    /**
     * Asserts relevant info inside a [SensitiveAppLockStatsLog] object for the
     * [SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED] atom.
     */
    fun assertSensitiveAppLockAtom(
        statsLogItem: StatsLogItem,
        pinSet: Boolean,
        lockedPackages: List<Int>,
        profileLocked: Boolean,
    ) {
        assertThat(statsLogItem.atomId())
            .isEqualTo(SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED)
        val atomBytes = getByteBuffer(statsLogItem)

        // skip header
        atomBytes.position(HEADER)

        // pin set
        assertBoolean(atomBytes, pinSet)

        // locked packages
        val field = parseFieldMetaData(atomBytes)
        assertThat(field.type).isEqualTo(TYPE_LIST)
        assertThat(atomBytes.get()).isEqualTo(lockedPackages.size.toByte())
        assertThat(atomBytes.get()).isEqualTo(TYPE_INT) // Element type
        for (pkg in lockedPackages) {
            assertThat(atomBytes.getInt()).isEqualTo(pkg)
        }
        skipAnnotations(atomBytes, field.annotationCount)

        // profile lock
        assertBoolean(atomBytes, profileLocked)
    }

    /**
     * Asserts relevant info inside a [SensitiveAppLockStatsLog] object for the
     * [SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED] atom.
     */
    fun assertSensitiveAppLockEventAtom(
        statsLogItem: StatsLogItem,
        packageUid: Int = NULL_PACKAGE_UID,
        appLockEvent: AppLockEvent = AppLockEvent.UNSPECIFIED,
        signInEvent: SignInEvent = SignInEvent.UNSPECIFIED,
        recoveryEvent: RecoveryEvent = RecoveryEvent.UNSPECIFIED,
    ) {
        assertThat(statsLogItem.atomId())
            .isEqualTo(SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED)
        val atomBytes = getByteBuffer(statsLogItem)

        // skip header
        atomBytes.position(HEADER)

        // packageUid
        assertInt(atomBytes, packageUid)

        // appLockEvent
        assertInt(atomBytes, appLockEvent.value)

        // signInEvent
        assertInt(atomBytes, signInEvent.value)

        // recoveryEvent
        assertInt(atomBytes, recoveryEvent.value)
    }

    /** Returns a list of [StatsLogItem] that are related to the SensitiveAppLock. */
    fun getAppLockAtoms(): List<StatsLogItem> {
        val atoms = ShadowStatsLog.getStatsLogs()
        return atoms.filter {
            if (it.atomId() == SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_EVENT_REPORTED) {
                return@filter true
            }
            if (it.atomId() == SensitiveAppLockStatsLog.SENSITIVE_APP_LOCK_STATE_CHANGED) {
                return@filter true
            }
            return@filter false
        }
    }

    private fun assertBoolean(buffer: ByteBuffer, expected: Boolean) {
        val field = parseFieldMetaData(buffer)
        assertThat(field.type).isEqualTo(TYPE_BOOLEAN)
        assertThat(buffer.get()).isEqualTo(expected.toByte())
        skipAnnotations(buffer, field.annotationCount)
    }

    private fun assertInt(buffer: ByteBuffer, expected: Int) {
        val field = parseFieldMetaData(buffer)
        assertThat(field.type).isEqualTo(TYPE_INT)
        assertThat(buffer.getInt()).isEqualTo(expected)
        skipAnnotations(buffer, field.annotationCount)
    }

    private fun Boolean.toByte(): Byte {
        if (this) {
            return 1.toByte()
        }
        return 0.toByte()
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
        return FieldMetaData(type = typeWithAnnotation and MASK, annotationCount)
    }

    private fun getByteBuffer(item: StatsLogItem): ByteBuffer {
        return ByteBuffer.wrap(item.bytes()).order(ByteOrder.LITTLE_ENDIAN)
    }

    private data class FieldMetaData(val type: Byte, val annotationCount: Int)

    // This is borrowed from android.util.StatsEvent
    private const val TYPE_INT: Byte = 0x00
    private const val TYPE_LIST: Byte = 0x03
    private const val TYPE_BOOLEAN: Byte = 0x05
    private const val MASK: Byte = 0x0F
    private const val HEADER = 16
    private const val NULL_PACKAGE_UID = -1
}
