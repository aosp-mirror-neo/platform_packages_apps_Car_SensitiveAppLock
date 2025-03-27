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

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** A fragment that displays the Validate Pin screen used for unlocking. */
@AndroidEntryPoint(Fragment::class)
class ValidatePinFragment : Hilt_ValidatePinFragment(R.layout.base_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    private lateinit var pinPad: PinPadView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.title).text = getString(R.string.validate_pin_title)

        pinPad = view.findViewById(R.id.pin_pad)
        pinPad.onConfirmClick = { validatePin() }
    }

    private fun validatePin() {
        lifecycleScope.launch {
            if (viewModel.isSavedPin(pinPad.getPin())) {
                logger.d("User entered the correct pin.")
                parentFragmentManager.setFragmentResult(
                    PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                    Bundle(),
                )
            }
        }
    }

    private companion object {
        val logger = Logger(ValidatePinFragment::class.java)
    }
}
