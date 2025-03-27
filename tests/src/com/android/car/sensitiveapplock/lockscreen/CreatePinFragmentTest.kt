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
package com.android.car.sensitiveapplock.lockscreen

import android.app.Application
import android.widget.ImageButton
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class CreatePinFragmentTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun onViewCreated_titlesSet() {
        launchFragmentInHiltContainer<CreatePinFragment>() { fragment ->
            val title = fragment.requireView().findViewById<TextView>(R.id.title).text.toString()
            val subtitle =
                fragment.requireView().findViewById<TextView>(R.id.subtitle).text.toString()

            assertThat(title).isEqualTo(context.getString(R.string.create_pin_title))
            assertThat(subtitle).isEqualTo(context.getString(R.string.create_pin_subtitle))
        }
    }

    @Test
    fun onEnterKeyClick_ifValidPinFormat_navigatesToConfirmPinScreen() {
        val navController =
            TestNavHostController(context).apply { setGraph(R.navigation.pin_nav_graph) }

        launchFragmentInHiltContainer<CreatePinFragment>() { fragment ->
            Navigation.setViewNavController(fragment.requireView(), navController)
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // 4 digit pin before hitting enter
            for (i in 0..3) {
                pinPadZeroKey.performClick()
            }
            pinPadEnterKey.performClick()

            assertThat(fragment.findNavController().currentDestination?.id)
                .isEqualTo(R.id.confirm_pin)
        }
    }

    @Test
    fun onEnterKeyClick_ifInvalidPinFormat_doesNotNavigateToConfirmPinScreen() {
        val navController =
            TestNavHostController(context).apply { setGraph(R.navigation.pin_nav_graph) }

        launchFragmentInHiltContainer<CreatePinFragment>() { fragment ->
            Navigation.setViewNavController(fragment.requireView(), navController)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // Pin currently empty so invalid
            pinPadEnterKey.performClick()

            assertThat(fragment.findNavController().currentDestination?.id)
                .isEqualTo(R.id.create_pin)
        }
    }
}
