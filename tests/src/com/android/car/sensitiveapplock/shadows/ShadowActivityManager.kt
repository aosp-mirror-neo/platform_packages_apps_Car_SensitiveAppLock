/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.sensitiveapplock.shadows

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import com.android.car.sensitiveapplock.shadows.ShadowActivityManager.Companion.clearedApplicationUserDataPackages
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Shadow of [ActivityManager]. */
@Implements(ActivityManager::class)
class ShadowActivityManager {
    private lateinit var context: Context

    @RealObject lateinit var realObject: ActivityManager

    @Implementation
    fun __constructor__(context: Context, handler: Handler?) {
        Shadow.invokeConstructor(
            ActivityManager::class.java,
            realObject,
            ClassParameter.from<Context?>(Context::class.java, context),
            ClassParameter.from<Handler?>(Handler::class.java, handler),
        )
        this.context = context
    }

    @Implementation
    fun clearApplicationUserData() {
        val packageName = context.packageName
        clearedApplicationUserDataPackages.add(packageName)
    }

    fun getClearedApplicationUserDataPackages(): List<String> =
        clearedApplicationUserDataPackages.toList()

    companion object {
        private val clearedApplicationUserDataPackages = mutableListOf<String>()

        fun reset() {
            clearedApplicationUserDataPackages.clear()
        }
    }
}
