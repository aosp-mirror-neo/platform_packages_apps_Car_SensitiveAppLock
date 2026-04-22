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

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.metrics.SignInEvent
import com.android.car.sensitiveapplock.util.Logger
import com.android.car.ui.core.CarUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A fragment that displays the Confirm Pin screen used for confirming a created PIN. */
@AndroidEntryPoint(Fragment::class)
class ConfirmPinFragment : Hilt_ConfirmPinFragment(R.layout.fragment_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    @Inject lateinit var registry: ActivityResultRegistry
    @Inject lateinit var metricsLogger: MetricsLogger

    private lateinit var addAccountLauncher: ActivityResultLauncher<Intent>
    private lateinit var pinLockView: PinLockView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addAccountLauncher =
            registerForActivityResult(StartActivityForResult(), registry, ::onAddAccountComplete)
    }

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

        pinLockView =
            view.findViewById<PinLockView>(R.id.pin_lock_view).apply {
                setTitle(R.string.confirm_pin_title)
                setSubtitle(R.string.confirm_pin_subtitle)

                setupActionButtons(
                    nextButton = CarUi.requireToolbar(requireActivity()).menuItems.first()
                ) {
                    confirmPin()
                }
            }
    }

    private fun confirmPin() {
        if (pinLockView.getPin() != viewModel.enteredPin.value) {
            logger.d("Pin did not match!")
            pinLockView.setError(R.string.pin_error)
            return
        }
        logger.d("Pin matches")
        lifecycleScope.launch {
            if (!viewModel.enableReAuthRecoveryFlow()) {
                logger.v("User not signed-in. Showing sign-in dialog.")
                showSignInDialog()
                return@launch
            }
            logger.d("User already signed-in. Confirming pin!")
            metricsLogger.logSignInEvent(SignInEvent.USER_ALREADY_SIGNED_IN)
            setResult(signedIn = true)
        }
    }

    private fun showSignInDialog() {
        val dialog =
            AlertDialog.Builder(context)
                .apply {
                    setTitle(R.string.signin_dialog_title)
                    setMessage(R.string.signin_dialog_message)
                    setCancelable(false)
                    setOnCancelListener { setResult() }
                    setNeutralButton(R.string.signin_dialog_neutral_button_text) { _, _ ->
                        lifecycleScope.launch {
                            metricsLogger.logSignInEvent(SignInEvent.USER_DECLINED_SIGN_IN)
                            setResult()
                        }
                    }
                    setPositiveButton(R.string.signin_dialog_positive_button_text) { _, _ ->
                        lifecycleScope.launch {
                            metricsLogger.logSignInEvent(SignInEvent.USER_STARTED_SIGN_IN)
                            addAccount()
                        }
                    }
                }
                .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private fun addAccount() =
        lifecycleScope.launch {
            val intent = viewModel.getAddAccountIntent()
            if (intent == null) {
                logger.d("Could not fetch intent to add account")
                setResult()
                return@launch
            }
            logger.d("Starting add account launcher")
            addAccountLauncher.launch(intent)
        }

    private fun onAddAccountComplete(result: ActivityResult) =
        lifecycleScope.launch {
            val signedIn = viewModel.enableReAuthRecoveryFlow()
            if (signedIn) {
                metricsLogger.logSignInEvent(SignInEvent.USER_COMPLETED_SIGN_IN)
            }
            setResult(signedIn)
        }

    private fun setResult(signedIn: Boolean = false) {
        val bundle =
            Bundle().apply {
                putString(PinLockActivity.USER_PIN_BUNDLE_KEY, viewModel.enteredPin.value)
                putBoolean(USER_SIGNED_IN_BUNDLE_KEY, signedIn)
            }
        parentFragmentManager.setFragmentResult(PinLockActivity.USER_PIN_REQUEST_KEY, bundle)
    }

    companion object {
        private val logger = Logger(ConfirmPinFragment::class.java)

        const val USER_SIGNED_IN_BUNDLE_KEY = "user_signed_in_bundle_key"
    }
}
