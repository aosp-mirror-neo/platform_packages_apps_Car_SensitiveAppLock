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
package com.android.car.sensitiveapplock.settings

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.lockscreen.PinLockActivity
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.settings.SettingsLockStatus.CANCELED_PIN
import com.android.car.sensitiveapplock.settings.SettingsLockStatus.VALID_PIN
import com.android.car.sensitiveapplock.util.Logger
import com.android.car.ui.baselayout.Insets
import com.android.car.ui.baselayout.InsetsChangedListener
import com.android.car.ui.core.CarUi.requireToolbar
import com.android.car.ui.toolbar.NavButtonMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Activity housing the Settings screen. */
@AndroidEntryPoint(FragmentActivity::class)
class SettingsActivity : Hilt_SettingsActivity(), InsetsChangedListener {
    private val viewModel: SettingsViewModel by viewModels()

    @Inject lateinit var metricsLogger: MetricsLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.isGuestUser()) {
            Toast.makeText(this, R.string.guest_user_exit_toast_message, Toast.LENGTH_SHORT).show()
            logger.v("Early exit on guest user")
            finish()
            return
        }
        setContentView(R.layout.activity_settings)
        requireToolbar(this).navButtonMode = NavButtonMode.BACK

        lifecycleScope.launch {
            // If this Activity has been recreated we probably saw a config change so don't reinit
            // to avoid showing the lockscreen again.
            if (savedInstanceState == null) {
                lockSettingsIfPinSet()
            }
            metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_SETTINGS_SCREEN_OPENED)

            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    when (uiState.settingsLockStatus) {
                        VALID_PIN -> {
                            // Display the fragment as the main content if not already there
                            if (supportFragmentManager.fragments.isEmpty()) {
                                logger.d("Initializing UI")
                                supportFragmentManager
                                    .beginTransaction()
                                    .replace(android.R.id.content, SettingsFragment())
                                    .commitNow()
                            }
                        }
                        CANCELED_PIN -> finish()
                        else -> return@collect
                    }
                }
            }
        }
    }

    @VisibleForTesting
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        lockSettingsIfPinSet()
        lifecycleScope.launch {
            metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_SETTINGS_SCREEN_OPENED)
        }
    }

    private fun lockSettingsIfPinSet() {
        lifecycleScope.launch {
            if (viewModel.isPinSet()) {
                viewModel.lockSettings()
                logger.d("Launching pin lock.")
                val pinLockIntent =
                    Intent(applicationContext, PinLockActivity::class.java).apply {
                        action = PinLockActivity.ACTION_VALIDATE_PIN
                        flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
                    }
                startActivity(pinLockIntent)
            } else {
                viewModel.unlockSettings()
            }
        }
    }

    override fun onCarUiInsetsChanged(insets: Insets) {
        // Todo: This empty function fixes duplicating insets in [SettingsFragment].
    }

    private companion object {
        val logger = Logger(SettingsActivity::class.java)
    }
}
