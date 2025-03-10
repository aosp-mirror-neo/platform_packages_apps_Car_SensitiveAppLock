package com.android.car.sensitiveapplock.lockscreen

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.android.car.sensitiveapplock.R

/** A custom view for a Pin Pad. */
class PinPadView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    private val enteredPin: EditText

    /** Action to take on confirm button click. */
    var onConfirmClick: () -> Unit = {}

    init {
        inflate(context, R.layout.pin_pad, this)
        enteredPin = findViewById(R.id.entered_pin)
        setupButtons()
    }

    /** Sets the entered PIN. */
    fun setPin(pin: String) {
        enteredPin.setText(pin)
    }

    /** Gets the entered PIN. */
    fun getPin() = enteredPin.text.toString()

    private fun setupButtons() {
        for ((id, value) in DIGIT_KEY_ID_VALUE_MAP) {
            val pinKey = findViewById<TextView>(id)
            pinKey.setOnClickListener { enteredPin.append(value) }
        }

        findViewById<TextView>(R.id.key_backspace).apply {
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

        findViewById<TextView>(R.id.key_confirm).setOnClickListener { onConfirmClick() }
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
