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
package com.android.car.sensitiveapplock.settings

import com.android.car.sensitiveapplock.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Enum class representing the lock status of the Settings screen. */
enum class SettingsLockStatus {
    UNSET,
    VALID_PIN,
    CANCELED_PIN,
}

/**
 * Manager for handling the lock status of the Settings screen.
 *
 * The dual-paned Settings screen must start the Pin Lock screen with `FLAG_ACTIVITY_NEW_TASK` to
 * have it be fullscreen, so this manager is used to communicate between the two.
 */
@Singleton
class SettingsLockManager @Inject constructor() {
    private val _lockStatusFlow = MutableStateFlow(SettingsLockStatus.UNSET)
    val lockStatusFlow: StateFlow<SettingsLockStatus> = _lockStatusFlow

    /** Sets the Settings screen lock status. */
    fun setLockStatus(status: SettingsLockStatus) {
        logger.d("Lock status changed to $status")
        _lockStatusFlow.value = status
    }

    private companion object {
        val logger = Logger(SettingsLockManager::class.java)
    }
}
