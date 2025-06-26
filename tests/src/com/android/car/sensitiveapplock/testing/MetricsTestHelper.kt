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

import com.android.car.sensitiveapplock.metrics.SensitiveAppLockStatsLog
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import org.robolectric.shadows.ShadowStatsLog.StatsLogItem

/** Object containing helper methods for testing Metrics. */
object MetricsTestHelper {

    /** Asserts relevant info inside a [SensitiveAppLockStatsLog] object. */
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
}
