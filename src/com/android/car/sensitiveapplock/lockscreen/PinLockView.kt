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
import androidx.annotation.DimenRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.util.Logger
import com.android.car.sensitiveapplock.util.ScreenSizeUtils
import com.android.car.sensitiveapplock.util.ScreenSizeUtils.getUsableBounds
import com.android.car.ui.toolbar.MenuItem
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
    private val title: TextView
    private val subtitle: TextView
    private val footer: TextView
    private val pinPad: PinPadView
    private val isPortrait: Boolean = ScreenSizeUtils.isPortrait(context)

    init {
        if (isPortrait) {
            logger.d("Inflating portrait layout.")
            inflate(context, R.layout.pin_lock_screen_portrait, this)
        } else {
            logger.d("Inflating landscape layout.")
            inflate(context, R.layout.pin_lock_screen, this)
        }

        enteredPin = findViewById(R.id.entered_pin)
        title = findViewById(R.id.title)
        subtitle = findViewById(R.id.subtitle)
        footer = findViewById(R.id.button_recovery)
        pinPad = findViewById(R.id.pin_pad)

        applyLayoutFromDimensions()
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

    /** Sets up the button actions. */
    fun setupActionButtons(nextButton: MenuItem? = null, onConfirmClick: () -> Unit = {}) {
        nextButton?.isVisible = true

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
                    val currPinHasValidFormat = pinManager.doesPinHaveValidFormat(s.toString())

                    // Only change button states if there was a change
                    if (currPinHasValidFormat != prevPinHasValidFormat) {
                        nextButton?.isEnabled = currPinHasValidFormat
                        pinPad.setConfirmEnabled(currPinHasValidFormat)

                        prevPinHasValidFormat = currPinHasValidFormat
                    }

                    // Only reset error if currently showing it
                    if (subtitle.text == context.getString(R.string.pin_error)) {
                        resetError()
                    }
                }
            }
        )

        pinPad.setupButtons(enteredPin, onConfirmClick)
        nextButton?.isEnabled = false
        nextButton?.onClickListener = MenuItem.OnClickListener { onConfirmClick() }
    }

    private fun applyLayoutFromDimensions() {
        val (usableWidth, usableHeight) = getUsableBounds(context)
        logger.d("Usable width = $usableWidth, usableHeight = $usableHeight")

        if (isPortrait) {
            applyPortraitLayoutFromDimensions(usableHeight)
        } else {
            applyLandscapeLayoutFromDimensions(usableHeight, usableWidth)
        }
    }

    private fun applyPortraitLayoutFromDimensions(height: Int) {
        val (textAreaConfig, pinAreaConfig) =
            when {
                height >= PORTRAIT_UNCONSTRAINED_THRESHOLD -> {
                    TextAreaConfig.UNCONSTRAINED_PORTRAIT to PinAreaConfig.UNCONSTRAINED_PORTRAIT
                }
                height >= PORTRAIT_DEFAULT_THRESHOLD -> {
                    TextAreaConfig.DEFAULT_PORTRAIT to PinAreaConfig.DEFAULT_PORTRAIT
                }
                else -> {
                    TextAreaConfig.COMPACT_PORTRAIT to PinAreaConfig.COZY_PORTRAIT
                }
            }
        applyLayoutConfig(textAreaConfig, pinAreaConfig)
    }

    private fun applyLandscapeLayoutFromDimensions(height: Int, width: Int) {
        val (textAreaConfig, pinAreaConfig) =
            when {
                height >= UNCONSTRAINED_TALL_THRESHOLD -> {
                    TextAreaConfig.UNCONSTRAINED_TALL to PinAreaConfig.UNCONSTRAINED_TALL
                }
                height >= UNCONSTRAINED_THRESHOLD -> {
                    TextAreaConfig.UNCONSTRAINED to PinAreaConfig.UNCONSTRAINED
                }
                height >= DEFAULT_THRESHOLD -> {
                    if (width >= WIDTH_THRESHOLD) {
                        TextAreaConfig.UNCONSTRAINED to PinAreaConfig.DEFAULT
                    } else {
                        TextAreaConfig.DEFAULT to PinAreaConfig.DEFAULT
                    }
                }
                height >= COMPACT_THRESHOLD -> {
                    TextAreaConfig.COMPACT to PinAreaConfig.COZY
                }
                else -> {
                    TextAreaConfig.COMPACT to PinAreaConfig.COMPACT
                }
            }
        applyLayoutConfig(
            textAreaConfig,
            pinAreaConfig,
            isWidescreen = width >= WIDESCREEN_THRESHOLD,
        )
    }

    private fun applyLayoutConfig(
        textAreaConfig: TextAreaConfig,
        pinAreaConfig: PinAreaConfig,
        isWidescreen: Boolean = false,
    ) {
        logger.i(
            "applyLayoutConfig: textAreaConfig = $textAreaConfig, " +
                "pinAreaConfig = $pinAreaConfig, isPortrait = $isPortrait," +
                " isWideScreen = $isWidescreen"
        )
        if (isPortrait) {
            val layout = findViewById<ConstraintLayout>(R.id.pin_lock_screen_portrait_layout)
            ConstraintSet().apply {
                clone(layout)
                applyTextAreaConfig(textAreaConfig, this)
                applyPinAreaConfig(pinAreaConfig, this)
                applyTo(layout)
            }
        } else {
            val leftLayout = findViewById<ConstraintLayout>(R.id.layout_left)
            val rightLayout = findViewById<ConstraintLayout>(R.id.layout_right)

            ConstraintSet().apply {
                clone(leftLayout)
                applyTextAreaConfig(textAreaConfig, this)
                applyTo(leftLayout)
            }
            ConstraintSet().apply {
                clone(rightLayout)
                applyPinAreaConfig(pinAreaConfig, this)
                applyTo(rightLayout)
            }

            if (isWidescreen) {
                val rootLayout = findViewById<ConstraintLayout>(R.id.pin_lock_screen_layout)
                val leftMarginPx =
                    resources.getDimensionPixelSize(R.dimen.pin_lock_screen_widescreen_left_margin)
                ConstraintSet().apply {
                    clone(rootLayout)
                    setHorizontalBias(R.id.layout_left, LEFT_BIAS)
                    setMargin(R.id.layout_left, ConstraintSet.START, leftMarginPx)
                    applyTo(rootLayout)
                }
            }
        }
    }

    private fun applyTextAreaConfig(textAreaConfig: TextAreaConfig, constraintSet: ConstraintSet) {
        applyTextConfig(textAreaConfig.textConfig)
        constraintSet.setMargin(
            R.id.title,
            ConstraintSet.TOP,
            resources.getDimensionPixelSize(textAreaConfig.topMarginDpResId),
        )

        val recoveryButtonId = R.id.button_recovery
        val recoveryButtonMarginPx =
            resources.getDimensionPixelSize(textAreaConfig.recoveryButtonMarginDpResId)
        if (isPortrait) {
            constraintSet.setMargin(recoveryButtonId, ConstraintSet.TOP, recoveryButtonMarginPx)
            return
        }

        constraintSet.apply {
            clear(recoveryButtonId, ConstraintSet.TOP)
            clear(recoveryButtonId, ConstraintSet.BOTTOM)

            // If compact, the recovery button should be anchored to the bottom of the screen
            // instead of to the pin entry box.
            if (textAreaConfig == TextAreaConfig.COMPACT) {
                connect(
                    recoveryButtonId,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                )
                setMargin(recoveryButtonId, ConstraintSet.BOTTOM, recoveryButtonMarginPx)
            } else {
                connect(recoveryButtonId, ConstraintSet.TOP, R.id.entered_pin, ConstraintSet.BOTTOM)
                setMargin(recoveryButtonId, ConstraintSet.TOP, recoveryButtonMarginPx)
            }
        }
    }

    private fun applyPinAreaConfig(pinAreaConfig: PinAreaConfig, constraintSet: ConstraintSet) {
        constraintSet.setMargin(
            R.id.pin_pad,
            ConstraintSet.TOP,
            resources.getDimensionPixelSize(pinAreaConfig.pinPadMarginDpResId),
        )
        pinPad.applyPinPadConfig(pinAreaConfig.pinPadConfig)
    }

    private fun applyTextConfig(textConfig: TextConfig) {
        // `setTextSize` without a unit defined will assume SP but `getDimension` converts the input
        // SP to PX. We specify PX here to avoid a double conversion.
        title.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(textConfig.titleSizeResId),
        )
        subtitle.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(textConfig.subtitleSizeResId),
        )
        footer.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(textConfig.footerSizeResId),
        )
    }

    private enum class TextAreaConfig(
        val textConfig: TextConfig,
        @param:DimenRes val topMarginDpResId: Int,
        @param:DimenRes val recoveryButtonMarginDpResId: Int,
    ) {
        UNCONSTRAINED(
            TextConfig.DEFAULT,
            R.dimen.pin_lock_screen_text_area_top_margin_unconstrained,
            R.dimen.pin_lock_screen_recovery_button_margin_unconstrained,
        ),
        UNCONSTRAINED_TALL(
            TextConfig.DEFAULT,
            R.dimen.pin_lock_screen_text_area_top_margin_unconstrained_tall,
            R.dimen.pin_lock_screen_recovery_button_margin_unconstrained,
        ),
        DEFAULT(
            TextConfig.COMPACT,
            R.dimen.pin_lock_screen_text_area_top_margin_default,
            R.dimen.pin_lock_screen_recovery_button_margin_default,
        ),
        COMPACT(
            TextConfig.COMPACT,
            R.dimen.pin_lock_screen_text_area_top_margin_compact,
            R.dimen.pin_lock_screen_recovery_button_margin_compact,
        ),
        UNCONSTRAINED_PORTRAIT(
            TextConfig.DEFAULT,
            R.dimen.pin_lock_screen_text_area_top_margin_unconstrained_portrait,
            R.dimen.pin_lock_screen_recovery_button_margin_unconstrained_portrait,
        ),
        DEFAULT_PORTRAIT(
            TextConfig.DEFAULT,
            R.dimen.pin_lock_screen_text_area_top_margin_default_portrait,
            R.dimen.pin_lock_screen_recovery_button_margin_default_portrait,
        ),
        COMPACT_PORTRAIT(
            TextConfig.COMPACT,
            R.dimen.pin_lock_screen_text_area_top_margin_compact_portrait,
            R.dimen.pin_lock_screen_recovery_button_margin_compact_portrait,
        ),
    }

    private enum class PinAreaConfig(
        val pinPadConfig: PinPadView.PinPadConfig,
        @param:DimenRes val pinPadMarginDpResId: Int,
    ) {
        UNCONSTRAINED(
            PinPadView.PinPadConfig.UNCONSTRAINED,
            R.dimen.pin_lock_screen_pin_area_top_margin_unconstrained,
        ),
        UNCONSTRAINED_TALL(
            PinPadView.PinPadConfig.UNCONSTRAINED,
            R.dimen.pin_lock_screen_pin_area_top_margin_unconstrained_tall,
        ),
        DEFAULT(
            PinPadView.PinPadConfig.DEFAULT,
            R.dimen.pin_lock_screen_pin_area_top_margin_default,
        ),
        COZY(PinPadView.PinPadConfig.COZY, R.dimen.pin_lock_screen_pin_area_top_margin_cozy),
        COMPACT(
            PinPadView.PinPadConfig.COMPACT,
            R.dimen.pin_lock_screen_pin_area_top_margin_compact,
        ),
        UNCONSTRAINED_PORTRAIT(
            PinPadView.PinPadConfig.UNCONSTRAINED,
            R.dimen.pin_lock_screen_pin_area_top_margin_unconstrained_portrait,
        ),
        DEFAULT_PORTRAIT(
            PinPadView.PinPadConfig.DEFAULT,
            R.dimen.pin_lock_screen_pin_area_top_margin_default_portrait,
        ),
        COZY_PORTRAIT(
            PinPadView.PinPadConfig.COZY,
            R.dimen.pin_lock_screen_pin_area_top_margin_cozy_portrait,
        ),
    }

    private enum class TextConfig(
        @param:DimenRes val titleSizeResId: Int,
        @param:DimenRes val subtitleSizeResId: Int,
        @param:DimenRes val footerSizeResId: Int,
    ) {
        DEFAULT(
            R.dimen.pin_lock_screen_title_text_size_default,
            R.dimen.pin_lock_screen_subtitle_text_size_default,
            R.dimen.pin_lock_screen_footer_text_size_default,
        ),
        COMPACT(
            R.dimen.pin_lock_screen_title_text_size_compact,
            R.dimen.pin_lock_screen_subtitle_text_size_compact,
            R.dimen.pin_lock_screen_footer_text_size_compact,
        ),
    }

    private companion object {
        val logger = Logger(PinLockView::class.java)

        const val LEFT_BIAS = 0f
        const val WIDESCREEN_THRESHOLD = 1584
        const val PORTRAIT_UNCONSTRAINED_THRESHOLD = 1080
        const val PORTRAIT_DEFAULT_THRESHOLD = 968
        const val UNCONSTRAINED_THRESHOLD = 696
        const val UNCONSTRAINED_TALL_THRESHOLD = 856
        const val DEFAULT_THRESHOLD = 608
        const val WIDTH_THRESHOLD = 1224
        const val COMPACT_THRESHOLD = 480
    }
}
