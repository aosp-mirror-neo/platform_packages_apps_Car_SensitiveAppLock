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
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.lockscreen.PinLockActivity.Companion.ACTION_CREATE_PIN
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.metrics.RecoveryEvent
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** A fragment that displays the Validate Pin screen used for unlocking. */
@AndroidEntryPoint(Fragment::class)
class ValidatePinFragment : Hilt_ValidatePinFragment(R.layout.fragment_pin_screen) {
    private val viewModel: PinLockViewModel by activityViewModels()

    @Inject lateinit var activityResultRegistry: ActivityResultRegistry
    @Inject lateinit var metricsLogger: MetricsLogger

    private lateinit var pinLockView: PinLockView
    private lateinit var appUninstallResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var confirmCredentialsLauncher: ActivityResultLauncher<Intent>
    private lateinit var pinRecreateResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appUninstallResultLauncher =
            registerForActivityResult(
                StartActivityForResult(),
                activityResultRegistry,
                ::onAppUninstall,
            )
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
                setRecoveryAction { recoverPin() }
            }
    }

    private fun validatePin() {
        lifecycleScope.launch {
            if (viewModel.isSavedPin(pinLockView.getPin())) {
                logger.d("User entered the correct pin.")
                setResult()
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
                showSecurityResetDialog()
                metricsLogger.logRecoveryEvent(
                    RecoveryEvent.USER_STARTED_MANUAL_RESET_RECOVERY_FLOW
                )
                return@launch
            }
            logger.v("Starting reauth activity")
            metricsLogger.logRecoveryEvent(RecoveryEvent.USER_STARTED_REAUTH_RECOVERY_FLOW)
            confirmCredentialsLauncher.launch(intent)
        }

    private fun onAppUninstall(result: ActivityResult) {
        logger.d("Successfully uninstalled package:")
        if (result.resultCode == RESULT_OK) {
            uninstallAppsAndClearData()
        } else {
            showSecurityResetDialog()
        }
    }

    private fun onConfirmCredentials(result: ActivityResult) =
        lifecycleScope.launch {
            val authResult =
                result.data?.getBooleanExtra(AccountManager.KEY_BOOLEAN_RESULT, false) == true
            if (!authResult) {
                logger.d("Failed to reauth user")
                return@launch
            }
            logger.d("Successfully reauth user; Start pin recreation flow")
            metricsLogger.logRecoveryEvent(RecoveryEvent.USER_COMPLETED_REAUTH_RECOVERY_FLOW)
            startPinRecreateFlow()
        }

    private fun onPinRecreateComplete(result: ActivityResult) =
        lifecycleScope.launch {
            logger.d("Pin recreated with result:${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                metricsLogger.logRecoveryEvent(RecoveryEvent.USER_RECREATED_PIN)
                setResult()
            }
        }

    private fun setResult() {
        parentFragmentManager.setFragmentResult(PinLockActivity.VALIDATE_PIN_REQUEST_KEY, Bundle())
    }

    private suspend fun startPinRecreateFlow() {
        val pinScreenIntent =
            Intent(context, PinLockActivity::class.java).apply { action = ACTION_CREATE_PIN }
        pinRecreateResultLauncher.launch(pinScreenIntent)
        metricsLogger.logRecoveryEvent(RecoveryEvent.USER_STARTED_PIN_RECREATE_FLOW)
    }

    private fun uninstallAppsAndClearData() =
        lifecycleScope.launch {
            // Uninstall user apps
            val userApps = viewModel.getLockedUserApps()
            if (userApps.isNotEmpty()) {
                val uri = ("package:${userApps.first().packageName}").toUri()
                val uninstallIntent =
                    Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri).apply {
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    }
                appUninstallResultLauncher.launch(uninstallIntent)
                return@launch
            }
            showClearAppsDataDialog()
        }

    private fun showSecurityResetDialog() {
        AlertDialog.Builder(context)
            .apply {
                setTitle(R.string.reset_dialog_title)
                setMessage(R.string.reset_dialog_message)
                setNeutralButton(R.string.reset_dialog_neutral_button_text) { dialog, _ ->
                    dialog.dismiss()
                }
                setPositiveButton(R.string.reset_dialog_positive_button_text) { dialog, _ ->
                    dialog.dismiss()
                    uninstallAppsAndClearData()
                }
            }
            .create()
            .show()
    }

    private suspend fun showClearAppsDataDialog() {
        val systemApps = viewModel.getLockedSystemApps()
        if (systemApps.isEmpty()) {
            startPinRecreateFlow()
            return
        }
        val lastApp = systemApps.last().label
        val message =
            when (systemApps.size) {
                1 -> {
                    getString(R.string.clear_data_dialog_single_app_message, lastApp)
                }
                2 -> {
                    val firstApp = systemApps.first().label
                    getString(R.string.clear_data_dialog_two_app_message, firstApp, lastApp)
                }
                else -> {
                    val appNames = systemApps.dropLast(1).joinToString(", ") { it.label }
                    getString(R.string.clear_data_dialog_message, appNames, lastApp)
                }
            }
        AlertDialog.Builder(context)
            .apply {
                setTitle(R.string.clear_data_dialog_title)
                setMessage(message)
                setNeutralButton(R.string.clear_data_dialog_neutral_button_text) { dialog, _ ->
                    dialog.dismiss()
                    showSecurityResetDialog()
                }
                setPositiveButton(R.string.clear_data_dialog_positive_button_text) { dialog, _ ->
                    lifecycleScope.launch {
                        dialog.dismiss()
                        viewModel.clearSystemAppsData()
                        startPinRecreateFlow()
                    }
                }
            }
            .create()
            .show()
    }

    private companion object {
        val logger = Logger(ValidatePinFragment::class.java)
    }
}
