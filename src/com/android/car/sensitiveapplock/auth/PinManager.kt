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
package com.android.car.sensitiveapplock.auth

/** Manager for handling pin storage and encryption. */
interface PinManager {
    /** Returns the current [PinState]. */
    suspend fun getAppLockPinState(): PinState

    /**
     * Encrypts and stores the submitted PIN into the AppLockDataStore.
     *
     * Returns true if the pin was successfully set, false otherwise.
     */
    suspend fun setAppLockPin(pin: String): Boolean

    /** Clears the PIN from the AppLockDataStore. */
    suspend fun clearAppLockPin()

    /**
     * Verifies if the submitted PIN matches the existing PIN in storage. Returns true if the pin
     * matches, false otherwise.
     *
     * False may also be returned if pin encryption is not available.
     *
     * If [getAppLockPinState] returns [PinState.RESET_REQUIRED] the encrypted data is no longer
     * available due to missing encryption keys. The client needs to reset all user encrypted.
     */
    suspend fun verifyAppLockPin(pin: String): Boolean

    /** Resets and removes all pin encryption data. This method should be used sparingly. */
    suspend fun reset()

    /**
     * Returns whether the entered PIN is in a valid format. The PIN must be numerical with a
     * minimum of 4 digits and a maximum of 16 digits.
     */
    fun doesPinHaveValidFormat(pin: String): Boolean

    /** The status of the AppLock PIN. */
    enum class PinState {
        UNKNOWN,

        // The device has a PIN.
        SET,

        // The device does not have a PIN.
        UNSET,

        // The PIN encryption is not available. This may happen if encryption keys are not
        // available. The client needs to reset the PinManager and delete all user encrypted data.
        RESET_REQUIRED,
    }
}
