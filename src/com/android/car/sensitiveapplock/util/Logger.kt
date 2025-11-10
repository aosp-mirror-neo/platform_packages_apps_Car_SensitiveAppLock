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
package com.android.car.sensitiveapplock.util

import android.util.Log

/**
 * Utility class to log messages to logcat. The intended use for a [Logger] is to include one per
 * file, like this:
 *
 * `private val logger = Logger(this::class.java)`
 *
 * The logger will log statements in this format:
 *
 * `TAG: [PREFIX] MESSAGE`
 *
 * Where TAG is always `SensitiveAppLock` and PREFIX is derived from `class.simpleName`. This allows
 * us to differentiate logs while staying within the 23 character limit of the log tag.
 *
 * @see [Log]
 */
class Logger(cls: Class<*>) {
    private val prefix = "[${cls.simpleName}] "

    fun i(message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, prefix + message, throwable)
        }
    }

    fun d(message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, prefix + message, throwable)
        }
    }

    fun v(message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, prefix + message, throwable)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, prefix + message, throwable)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, prefix + message, throwable)
        }
    }

    private companion object {
        const val TAG = "SensitiveAppLock"
    }
}
