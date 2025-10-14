package com.android.car.sensitiveapplock.lockscreen

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.OrientationUtils.isPortrait

/** A custom view for a Pin Pad. */
class PinPadView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    init {
        if (isPortrait(context)) {
            inflate(context, R.layout.pin_pad_portrait, this)
        } else {
            inflate(context, R.layout.pin_pad, this)
        }
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

    private companion object {
        val DIGIT_KEY_ID_VALUE_MAP =
            hashMapOf<Int, String>(
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
    }
}
