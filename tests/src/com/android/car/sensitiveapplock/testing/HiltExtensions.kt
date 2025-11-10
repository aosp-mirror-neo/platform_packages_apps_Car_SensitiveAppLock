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

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.util.Preconditions
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

/**
 * Equivalent to [androidx.fragment.app.testing.launchFragmentInContainer] but with a Hilt-enabled
 * activity.
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    crossinline action: Fragment.() -> Unit = {},
    crossinline onActivity: (Activity) -> Unit = {},
    crossinline onFragment: (Fragment) -> Unit = {},
) {
    val startActivityIntent =
        Intent.makeMainActivity(
            ComponentName(ApplicationProvider.getApplicationContext(), HiltTestActivity::class.java)
        )
    ActivityScenario.launch<HiltTestActivity>(startActivityIntent).onActivity { activity ->
        onActivity(activity)
        activity.launchFragment<T>(fragmentArgs, onFragment).action()
    }
}

/** Launch and return the fragment within a Hilt-enabled activity. */
inline fun <reified T : Fragment> HiltTestActivity.launchFragment(
    fragmentArgs: Bundle? = null,
    onFragment: (Fragment) -> Unit = {},
): T {
    val fragment: Fragment =
        supportFragmentManager.fragmentFactory.instantiate(
            Preconditions.checkNotNull(T::class.java.classLoader),
            T::class.java.name,
        )
    fragment.arguments = fragmentArgs
    supportFragmentManager.beginTransaction().add(android.R.id.content, fragment, "").commitNow()
    onFragment(fragment)

    return fragment as T
}
