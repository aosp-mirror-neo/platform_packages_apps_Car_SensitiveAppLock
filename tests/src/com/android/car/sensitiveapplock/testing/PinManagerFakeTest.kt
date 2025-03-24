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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.auth.PinManager.PinState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinManagerFakeTest {
    private val pinManager = PinManagerFake()

    @Test
    fun getAppLockPinState_whenPinIsSet_returnsSetStatus() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        assertThat(pinManager.getAppLockPinState()).isEqualTo(PinState.SET)
    }

    @Test
    fun getAppLockPinState_whenPinIsNotSet_returnsUnsetStatus() = runTest {
        assertThat(pinManager.getAppLockPinState()).isEqualTo(PinState.UNSET)
    }

    @Test
    fun getAppLockPinState_whenEncryptionNotAvailable_returnsResetStatus() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        pinManager.encryptionFailure = true

        assertThat(pinManager.getAppLockPinState()).isEqualTo(PinState.RESET_REQUIRED)
    }

    @Test
    fun clearAppLockPin_shouldClearStoredPin() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        pinManager.clearAppLockPin()

        assertThat(pinManager.getAppLockPinState()).isEqualTo(PinState.UNSET)
    }

    @Test
    fun setAppLockPin_whenPinIsLessThan4digits_returnsFalse() = runTest {
        assertThat(pinManager.setAppLockPin(THREE_DIGIT_USER_PIN)).isFalse()
    }

    @Test
    fun setAppLockPin_whenPinIsMoreThan16digits_returnsFalse() = runTest {
        assertThat(pinManager.setAppLockPin(SEVENTEEN_DIGIT_USER_PIN)).isFalse()
    }

    @Test
    fun setAppLockPin_whenPinIsAlphanumeric_returnsFalse() = runTest {
        assertThat(pinManager.setAppLockPin(ALPHANUMERIC_PIN)).isFalse()
    }

    @Test
    fun setAppLockPin_whenPinIsValid_returnsTrue() = runTest {
        assertThat(pinManager.setAppLockPin(USER_PIN)).isTrue()
    }

    @Test
    fun verifyAppLockPin_onEncryptionFailure_returnsFalse() = runTest {
        pinManager.encryptionFailure = true

        assertThat(pinManager.verifyAppLockPin(USER_PIN)).isFalse()
    }

    @Test
    fun verifyAppLockPin_whenInputPinDoesNotMatchStoredPin_returnsFalse() = runTest {
        val invalidPin = "6952"
        pinManager.setAppLockPin(USER_PIN)

        assertThat(pinManager.verifyAppLockPin(invalidPin)).isFalse()
    }

    @Test
    fun verifyAppLockPin_whenPinNoSet_returnsFalse() = runTest {
        assertThat(pinManager.verifyAppLockPin(USER_PIN)).isFalse()
    }

    @Test
    fun verifyAppLockPin_whenInputPinMatchesStoredPin_returnsTrue() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        assertThat(pinManager.verifyAppLockPin(USER_PIN)).isTrue()
    }

    @Test
    fun reset_clearsEncryptionFailureAndStoredPin() = runTest {
        pinManager.setAppLockPin(USER_PIN)
        pinManager.encryptionFailure = true

        pinManager.reset()

        assertThat(pinManager.getAppLockPinState()).isEqualTo(PinState.UNSET)
    }

    private companion object {
        const val USER_PIN = "7354"
        const val ALPHANUMERIC_PIN = "s345a"
        const val THREE_DIGIT_USER_PIN = "984"
        const val SEVENTEEN_DIGIT_USER_PIN = "15987456321546987"
    }
}
