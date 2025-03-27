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
import androidx.activity.addCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint

/** A fragment that displays the Confirm Pin screen used for confirming a created PIN. */
@AndroidEntryPoint(Fragment::class)
class ConfirmPinFragment : Hilt_ConfirmPinFragment(R.layout.base_pin_screen) {
    val viewModel: PinLockViewModel by activityViewModels()

    lateinit var pinPad: PinPadView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*
         * CarUi doesn't provide a `setupWithNavController()` function for us to integrate with a
         * NavController so we use this dispatcher to control the behaviour ourselves.
         * The callback is enabled in the lambda and will be removed when the fragment is destroyed.
         * See https://developer.android.com/guide/navigation/navigation-custom-back#implement.
         */
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().navigate(R.id.action_confirm_pin_to_create_pin)
            isEnabled = true
        }

        view.apply {
            findViewById<TextView>(R.id.title).text = getString(R.string.confirm_pin_title)
            findViewById<TextView>(R.id.subtitle).text = getString(R.string.confirm_pin_subtitle)
        }

        pinPad = view.findViewById(R.id.pin_pad)

        PinFragmentUi.setupPinFragmentUi(
            pinFragmentView = view,
            doesPinHaveValidFormat = { pin -> viewModel.doesPinHaveValidFormat(pin) },
            onConfirmClicked = { confirmPin() },
        )
    }

    private fun confirmPin() {
        if (pinPad.getPin() != viewModel.enteredPin.value) {
            logger.w("Pin did not match!")
            PinFragmentUi.setErrorMessage(requireView(), getString(R.string.pin_error))
            return
        }

        logger.d("Pin matches! Confirming pin!")
        parentFragmentManager.setFragmentResult(
            PinLockActivity.USER_PIN_REQUEST_KEY,
            bundleOf(PinLockActivity.USER_PIN_BUNDLE_KEY to viewModel.enteredPin.value),
        )
    }

    private companion object {
        val logger = Logger(ConfirmPinFragment::class.java)
    }
}
