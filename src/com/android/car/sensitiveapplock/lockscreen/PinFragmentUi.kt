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

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.android.car.sensitiveapplock.R

/* Object that hooks up UI elements in a PinFragment. */
object PinFragmentUi {
    // Todo: b/415382791 - Create custom view for shared Pin Fragment behavior.

    /** Sets up a Pin Fragment's buttons and the PIN text. */
    fun setupPinFragmentUi(
        pinFragmentView: View,
        doesPinHaveValidFormat: (pin: String) -> Boolean,
        onConfirmClicked: () -> Unit,
    ) {
        val pinPad = pinFragmentView.findViewById<PinPadView>(R.id.pin_pad)
        val nextButton = pinFragmentView.findViewById<Button>(R.id.button_next)
        val enteredPin = pinFragmentView.findViewById<EditText>(R.id.entered_pin)

        // Disable buttons until a valid format PIN is entered
        enteredPin.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val hasValidFormat = doesPinHaveValidFormat(s.toString())
                    nextButton.isEnabled = hasValidFormat
                    pinPad.setConfirmEnabled(hasValidFormat)
                }
            }
        )

        pinPad.apply {
            this.enteredPin = enteredPin
            this.onConfirmClick = onConfirmClicked
        }
        nextButton.setOnClickListener { onConfirmClicked() }
    }

    /** Sets an error message for a Pin Fragment. */
    fun setErrorMessage(pinFragmentView: View, errorMessage: String) {
        val errorColorId =
            TypedValue()
                .apply {
                    pinFragmentView.context.theme.resolveAttribute(
                        android.R.attr.colorError,
                        this,
                        true,
                    )
                }
                .resourceId
        val errorColor = ContextCompat.getColor(pinFragmentView.context, errorColorId)

        pinFragmentView.findViewById<TextView>(R.id.subtitle).apply {
            text = errorMessage
            setTextColor(errorColor)
        }
        pinFragmentView.findViewById<EditText>(R.id.entered_pin).backgroundTintList =
            ColorStateList.valueOf(errorColor)
    }
}
