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

import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.auth.PinManager.PinState

/** Fake implementation of [PinManager]. */
class PinManagerFake : PinManager {
    private var pin: String = ""

    // Clients can set this value directly to test encryption failure handling.
    var encryptionFailure = false

    override suspend fun getAppLockPinState(): PinState {
        if (pin.isEmpty()) {
            return PinState.UNSET
        }
        if (encryptionFailure) {
            return PinState.RESET_REQUIRED
        }
        return PinState.SET
    }

    override suspend fun setAppLockPin(pin: String): Boolean {
        if (!doesPinHaveValidFormat(pin)) {
            return false
        }
        if (encryptionFailure) {
            return false
        }
        this.pin = pin
        return true
    }

    override suspend fun clearAppLockPin() {
        pin = ""
    }

    override suspend fun verifyAppLockPin(pin: String): Boolean {
        if (getAppLockPinState() == PinState.UNSET) {
            return false
        }
        if (encryptionFailure) {
            return false
        }
        return this.pin == pin
    }

    override suspend fun reset() {
        encryptionFailure = false
        clearAppLockPin()
    }

    override fun doesPinHaveValidFormat(pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH) {
            return false
        }
        if (pin.length > MAX_PIN_LENGTH) {
            return false
        }
        return pin.toIntOrNull() != null
    }

    private companion object {
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 16
    }
}
