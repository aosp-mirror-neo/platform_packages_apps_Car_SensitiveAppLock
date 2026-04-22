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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import com.android.car.ui.core.CarUi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** A fragment that displays the Create Pin screen used for creating a PIN. */
@AndroidEntryPoint(Fragment::class)
class CreatePinFragment : Hilt_CreatePinFragment(R.layout.fragment_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    private lateinit var pinLockView: PinLockView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinLockView =
            view.findViewById<PinLockView>(R.id.pin_lock_view).apply {
                setTitle(R.string.create_pin_title)
                setSubtitle(R.string.create_pin_subtitle)

                setupActionButtons(
                    nextButton = CarUi.requireToolbar(requireActivity()).menuItems.first()
                ) {
                    logger.d("Moving to confirm pin and temporarily saving entered pin.")
                    viewModel.setEnteredPin(getPin())
                    findNavController().navigate(R.id.action_create_pin_to_confirm_pin)
                }
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.enteredPin.collect { pin ->
                    if (pin.isNotEmpty()) {
                        logger.d("Temporary pin was not empty so displaying it.")
                        pinLockView.setPin(pin)
                    }
                }
            }
        }
    }

    private companion object {
        val logger = Logger(CreatePinFragment::class.java)
    }
}
