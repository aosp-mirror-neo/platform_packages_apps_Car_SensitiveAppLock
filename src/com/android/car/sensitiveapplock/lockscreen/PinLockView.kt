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

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.util.OrientationUtils.isPortrait
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A custom view for PinLock screens. */
@AndroidEntryPoint(FrameLayout::class)
class PinLockView @Inject constructor(context: Context, attrs: AttributeSet) :
    Hilt_PinLockView(context, attrs) {

    @Inject lateinit var pinManager: PinManager

    private var subtitleResId: Int = Resources.ID_NULL
    private var prevPinHasValidFormat = false

    private val enteredPin: EditText
    private val subtitle: TextView

    init {
        if (isPortrait(context)) {
            inflate(context, R.layout.pin_lock_screen_portrait, this)
        } else {
            inflate(context, R.layout.pin_lock_screen, this)
        }
        enteredPin = findViewById<EditText>(R.id.entered_pin)
        subtitle = findViewById<TextView>(R.id.subtitle)
    }

    /** Sets the view title. */
    fun setTitle(@StringRes title: Int) {
        findViewById<TextView>(R.id.title).setText(title)
    }

    /* Sets the view subtitle. */
    fun setSubtitle(@StringRes subtitle: Int) {
        this.subtitle.setText(subtitle)
        subtitleResId = subtitle
    }

    /** Enables the recovery button and sets the action to be performed on clicking it. */
    fun setRecoveryAction(recoveryFlow: () -> Unit) {
        findViewById<TextView>(R.id.button_recovery).apply {
            setOnClickListener { recoveryFlow() }
            visibility = VISIBLE
        }
    }

    /** Sets the error message. */
    fun setError(@StringRes message: Int) {
        val errorColor =
            with(context.theme) {
                val typedValue = TypedValue()
                resolveAttribute(android.R.attr.colorError, typedValue, true)
                ContextCompat.getColor(context, typedValue.resourceId)
            }

        subtitle.apply {
            setText(message)
            setTextColor(errorColor)
        }
        enteredPin.backgroundTintList = ColorStateList.valueOf(errorColor)
    }

    /** Resets the error message. */
    private fun resetError() {
        val defaultTextAppearance =
            with(context.theme) {
                val typedValue = TypedValue()
                resolveAttribute(android.R.attr.textAppearanceSmall, typedValue, true)
                typedValue.resourceId
            }
        subtitle.apply {
            text = if (subtitleResId != Resources.ID_NULL) context.getString(subtitleResId) else ""

            setTextAppearance(defaultTextAppearance)
        }

        val defaultColor =
            with(context.theme) {
                val typedValue = TypedValue()
                resolveAttribute(android.R.attr.colorControlNormal, typedValue, true)
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        enteredPin.backgroundTintList = ColorStateList.valueOf(defaultColor)
    }

    /** Sets the entered PIN. */
    fun setPin(pin: String) {
        enteredPin.setText(pin)
        enteredPin.setSelection(enteredPin.text.length)
    }

    /** Gets the entered PIN. */
    fun getPin() = enteredPin.text.toString()

    /** Sets up the UI button actions. */
    fun setupUi(nextButton: NextButton? = null, onConfirmClick: () -> Unit = {}) {
        nextButton?.setVisible(true)

        val pinPad = findViewById<PinPadView>(R.id.pin_pad)

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
                    val currHasValidFormat = pinManager.doesPinHaveValidFormat(s.toString())

                    // Only change button states if there was a change
                    if (currHasValidFormat != prevPinHasValidFormat) {
                        nextButton?.setEnabled(currHasValidFormat)
                        pinPad.setConfirmEnabled(currHasValidFormat)

                        prevPinHasValidFormat = currHasValidFormat
                    }

                    // Only reset error if currently showing it
                    if (subtitle.text == context.getString(R.string.pin_error)) {
                        resetError()
                    }
                }
            }
        )

        pinPad.setupButtons(enteredPin, onConfirmClick)
        nextButton?.setEnabled(false)
        nextButton?.setOnClickListener { onConfirmClick() }
    }

    /** An interface for the Next button on pin screens. */
    interface NextButton {
        /** Sets the enabled state of the button. */
        fun setEnabled(enabled: Boolean)

        /** Sets an onClickListener for the button. */
        fun setOnClickListener(listener: () -> Unit)

        /** Sets the visibility of the button. */
        fun setVisible(visible: Boolean)
    }
}
