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
import android.view.WindowInsets
import android.view.WindowManager
import com.android.car.sensitiveapplock.R
import kotlin.math.roundToInt

/* Object containing util functions for the screen size. */
object ScreenSizeUtils {

    /* Returns if device is in portrait mode. */
    fun isPortrait(context: Context): Boolean {
        val (usableWidth, usableHeight) = getUsableBounds(context)

        return (usableWidth <
            context.resources.getDimensionPixelSize(R.dimen.portrait_width_max_threshold)) &&
            (usableHeight >=
                context.resources.getDimensionPixelSize(R.dimen.portrait_height_min_threshold))
    }

    /** Returns the usable bounds of the application. */
    fun getUsableBounds(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val windowMetrics = windowManager.currentWindowMetrics.bounds
        val insets =
            windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
        val defaultToolbarHeight =
            context.resources.getDimensionPixelSize(R.dimen.toolbar_height_default)
        val density = context.resources.displayMetrics.density

        val usableHeight =
            ((windowMetrics.height() - insets.top - insets.bottom - defaultToolbarHeight) / density)
                .roundToInt()
                .coerceAtLeast(0)
        val usableWidth =
            ((windowMetrics.width() - insets.left - insets.right) / density)
                .roundToInt()
                .coerceAtLeast(0)
        return Pair(usableWidth, usableHeight)
    }
}
