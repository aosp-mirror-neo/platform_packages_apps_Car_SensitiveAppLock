package com.android.car.sensitiveapplock.lockscreen

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DimenRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.car.sensitiveapplock.R

/** A custom view for a Pin Pad. */
class PinPadView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    init {
        inflate(context, R.layout.pin_pad, this)
        // Adding an onClickListener automatically enables the button so disable it after
        setConfirmEnabled(false)
    }

    /** Sets the enabled state of the confirm button. */
    fun setConfirmEnabled(enabled: Boolean) {
        findViewById<ImageButton>(R.id.key_confirm).apply {
            isEnabled = enabled
            isClickable = enabled
        }
    }

    /** Sets up Pin Pad buttons. */
    fun setupButtons(enteredPin: EditText, onConfirmClick: () -> Unit = {}) {
        for ((id, value) in DIGIT_KEY_ID_VALUE_MAP) {
            val pinKey = findViewById<TextView>(id)
            pinKey.setOnClickListener { enteredPin.append(value) }
        }

        findViewById<ImageButton>(R.id.key_backspace).apply {
            setOnClickListener {
                val selectionEnd = enteredPin.selectionEnd
                if (selectionEnd <= 0) return@setOnClickListener
                if (enteredPin.text.isEmpty()) return@setOnClickListener

                enteredPin.text.delete(selectionEnd - 1, selectionEnd)
            }

            setOnLongClickListener {
                enteredPin.text.clear()
                true
            }
        }

        findViewById<ImageButton>(R.id.key_confirm).setOnClickListener { onConfirmClick() }
    }

    fun applyPinPadConfig(config: PinPadConfig) {
        val pinLayout = findViewById<ConstraintLayout>(R.id.pin_layout)

        val rowSpacingPx = resources.getDimensionPixelSize(config.rowSpacingDpResId)
        val keyHeightPx = resources.getDimensionPixelSize(config.keyHeightDpResId)
        ConstraintSet().apply {
            clone(pinLayout)
            applyRowSpacing(rowSpacingPx, this)
            applyKeyHeight(keyHeightPx, this)
            applyTo(pinLayout)
        }
    }

    private fun applyRowSpacing(rowSpacingPx: Int, constraintSet: ConstraintSet) {
        val rowIds = listOf(R.id.key_4, R.id.key_7, R.id.key_backspace)
        for (id in rowIds) {
            constraintSet.setMargin(id, ConstraintSet.TOP, rowSpacingPx)
        }
    }

    private fun applyKeyHeight(heightPx: Int, constraintSet: ConstraintSet) {
        val pinKeys = DIGIT_KEY_ID_VALUE_MAP.map { it.key } + ACTION_KEY_ID_VALUE_SET
        for (id in pinKeys) {
            constraintSet.constrainHeight(id, heightPx)
        }
    }

    enum class PinPadConfig(
        @param:DimenRes val keyHeightDpResId: Int,
        @param:DimenRes val rowSpacingDpResId: Int,
    ) {
        UNCONSTRAINED(
            R.dimen.pin_pad_key_height_unconstrained,
            R.dimen.pin_pad_row_spacing_unconstrained,
        ),
        DEFAULT(R.dimen.pin_pad_key_height_default, R.dimen.pin_pad_row_spacing_default),
        COZY(R.dimen.pin_pad_key_height_cozy, R.dimen.pin_pad_row_spacing_cozy),
        COMPACT(R.dimen.pin_pad_key_height_compact, R.dimen.pin_pad_row_spacing_compact),
    }

    private companion object {
        val DIGIT_KEY_ID_VALUE_MAP =
            hashMapOf(
                R.id.key_0 to "0",
                R.id.key_1 to "1",
                R.id.key_2 to "2",
                R.id.key_3 to "3",
                R.id.key_4 to "4",
                R.id.key_5 to "5",
                R.id.key_6 to "6",
                R.id.key_7 to "7",
                R.id.key_8 to "8",
                R.id.key_9 to "9",
            )
        val ACTION_KEY_ID_VALUE_SET = setOf(R.id.key_backspace, R.id.key_confirm)
    }
}
