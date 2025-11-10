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

import android.view.View
import android.widget.Button
import com.android.car.sensitiveapplock.lockscreen.PinLockView.NextButton
import com.android.car.ui.toolbar.MenuItem

/** Adapts a [Button] to behave as a [NextButton]. */
fun Button.asNextButton(): NextButton {
    return object : NextButton {
        override fun setEnabled(enabled: Boolean) {
            this@asNextButton.isEnabled = enabled
        }

        override fun setOnClickListener(listener: () -> Unit) {
            this@asNextButton.setOnClickListener { listener() }
        }

        override fun setVisible(visible: Boolean) {
            this@asNextButton.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}

/** Adapts a [MenuItem] to behave as a [NextButton]. */
fun MenuItem.asNextButton(): NextButton {
    return object : NextButton {
        override fun setEnabled(enabled: Boolean) {
            this@asNextButton.isEnabled = enabled
        }

        override fun setOnClickListener(listener: () -> Unit) {
            this@asNextButton.setOnClickListener { listener() }
        }

        override fun setVisible(visible: Boolean) {
            this@asNextButton.isVisible = visible
        }
    }
}
