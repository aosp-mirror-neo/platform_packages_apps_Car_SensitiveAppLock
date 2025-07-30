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

import android.accounts.AccountManager
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.lockscreen.PinLockActivity.Companion.ACTION_CREATE_PIN
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A fragment that displays the Validate Pin screen used for unlocking. */
@AndroidEntryPoint(Fragment::class)
class ValidatePinFragment : Hilt_ValidatePinFragment(R.layout.fragment_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    private lateinit var pinLockView: PinLockView

    @Inject lateinit var activityResultRegistry: ActivityResultRegistry
    private lateinit var confirmCredentialsLauncher: ActivityResultLauncher<Intent>
    private lateinit var pinRecreateResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        confirmCredentialsLauncher =
            registerForActivityResult(
                StartActivityForResult(),
                activityResultRegistry,
                ::onConfirmCredentials,
            )
        pinRecreateResultLauncher =
            registerForActivityResult(
                StartActivityForResult(),
                activityResultRegistry,
                ::onPinRecreateComplete,
            )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.title).text = getString(R.string.validate_pin_title)

        pinLockView =
            view.findViewById<PinLockView>(R.id.pin_lock_view).apply {
                setTitle(R.string.validate_pin_title)
                setupUi { validatePin() }
            }
        if (resources.getBoolean(R.bool.config_enablePinLockRecovery)) {
            pinLockView.setRecoveryAction { recoverPin() }
        }
    }

    private fun validatePin() {
        lifecycleScope.launch {
            if (viewModel.isSavedPin(pinLockView.getPin())) {
                logger.d("User entered the correct pin.")
                parentFragmentManager.setFragmentResult(
                    PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                    Bundle(),
                )
            } else {
                logger.d("User entered the wrong pin.")
                pinLockView.setError(R.string.pin_error)
            }
        }
    }

    private fun recoverPin() =
        lifecycleScope.launch {
            val intent = viewModel.getReAuthIntent()
            if (intent == null) {
                logger.d("Cannot recover pin; User has not setup recovery account")
                return@launch
            }
            logger.v("Starting reauth activity")
            confirmCredentialsLauncher.launch(intent)
        }

    private fun onConfirmCredentials(result: ActivityResult) {
        val authResult =
            result.data?.getBooleanExtra(AccountManager.KEY_BOOLEAN_RESULT, false) == true
        if (!authResult) {
            logger.d("Failed to reauth user")
            return
        }
        logger.d("Successfully reauth user; Start pin recreation flow")
        val pinScreenIntent =
            Intent(context, PinLockActivity::class.java).apply { action = ACTION_CREATE_PIN }
        pinRecreateResultLauncher.launch(pinScreenIntent)
    }

    private fun onPinRecreateComplete(result: ActivityResult) {
        logger.d("Pin recreated with result:${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch {
                parentFragmentManager.setFragmentResult(
                    PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                    Bundle(),
                )
            }
        }
    }

    private companion object {
        val logger = Logger(ValidatePinFragment::class.java)
    }
}
