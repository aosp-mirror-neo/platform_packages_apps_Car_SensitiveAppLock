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

import android.content.Context
import android.content.res.Configuration

/* Object containing util functions for screen orientation. */
object OrientationUtils {
    private const val SPLITSCREEN_MULTITASKING_FEATURE =
        "android.software.car.splitscreen_multitasking"

    /* Returns if device is in portrait mode. */
    fun isPortrait(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(SPLITSCREEN_MULTITASKING_FEATURE) ||
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }
}
