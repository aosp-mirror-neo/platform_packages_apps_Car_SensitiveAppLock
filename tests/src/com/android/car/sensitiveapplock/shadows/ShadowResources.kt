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

import android.annotation.BoolRes
import android.content.res.Resources
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

/** Shadow of [Resources]. */
@Implements(Resources::class)
class ShadowResources : org.robolectric.shadows.ShadowResources() {
    @RealObject lateinit var realResources: Resources

    @Implementation
    fun getBoolean(@BoolRes id: Int): Boolean {
        if (booleanResourceMap.containsKey(id)) {
            return booleanResourceMap.getOrDefault(id, false)
        }
         return Shadow.directlyOn(
            realResources,
            Resources::class.qualifiedName,
            "getBoolean",
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, id)
        )
    }

    companion object {
        private var booleanResourceMap = mutableMapOf<Int, Boolean>()

        fun setBoolean(id: Int, value: Boolean) {
            booleanResourceMap[id] = value
        }

        fun reset() {
            booleanResourceMap.clear()
        }
    }
}
