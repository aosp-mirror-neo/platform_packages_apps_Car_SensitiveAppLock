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
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class ValidatePinFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val hiltTestActivityRule = HiltTestActivityRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Inject lateinit var pinManager: PinManager

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun onViewCreated_titlesSet() {
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val title = fragment.requireView().findViewById<TextView>(R.id.title).text.toString()

            assertThat(title).isEqualTo(context.getString(R.string.validate_pin_title))
        }
    }

    @Test
    fun onEnterKeyClick_pinIsValid_setsFragmentResult() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // 4 digit pin before hitting enter
            for (i in 0..3) {
                pinPadZeroKey.performClick()
            }
            pinPadEnterKey.performClick()

            assertThat(receivedBundle).isNotNull()
        }
    }

    @Test
    fun onEnterKeyClick_pinIsNotValid_doesNotSetFragmentResult() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            assertThat(receivedBundle).isNull()
        }
    }

    private companion object {
        const val USER_PIN = "0000"
    }
}
