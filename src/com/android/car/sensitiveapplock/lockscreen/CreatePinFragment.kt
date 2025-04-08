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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** A fragment that displays the Create Pin screen used for creating a PIN. */
@AndroidEntryPoint(Fragment::class)
class CreatePinFragment : Hilt_CreatePinFragment(R.layout.base_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    private lateinit var pinPad: PinPadView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.create_pin_title)
            findViewById<TextView>(R.id.subtitle).text = getString(R.string.create_pin_subtitle)
        }

        pinPad = view.findViewById(R.id.pin_pad)
        pinPad.onConfirmClick = {
            if (!viewModel.doesPinHaveValidFormat(pinPad.getPin())) {
                logger.w("Entered PIN is not in valid format.")
            } else {
                viewModel.setEnteredPin(pinPad.getPin())
                view.findNavController().navigate(R.id.action_create_pin_to_confirm_pin)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enteredPin.collect { pin ->
                    if (pin.isNotEmpty()) {
                        pinPad.setPin(pin)
                    }
                }
            }
        }
    }

    private companion object {
        val logger = Logger(CreatePinFragment::class.java)
    }
}
