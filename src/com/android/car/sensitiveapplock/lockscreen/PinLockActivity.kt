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

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.metrics.MetricsLogger.Companion.NULL_PACKAGE_UID
import com.android.car.sensitiveapplock.util.Logger
import com.android.car.sensitiveapplock.util.OrientationUtils.isPortrait
import com.android.car.ui.core.CarUi.requireToolbar
import com.android.car.ui.toolbar.MenuItem
import com.android.car.ui.toolbar.NavButtonMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Activity that presents a Pin Lock screen to users. */
@AndroidEntryPoint(AppCompatActivity::class)
class PinLockActivity : Hilt_PinLockActivity() {
    private val viewModel: PinLockViewModel by viewModels()

    @Inject lateinit var metricsLogger: MetricsLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_pin_lock)
        requireToolbar(this).navButtonMode = NavButtonMode.BACK

        when (intent.action) {
            Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS -> initializeAppUnlockFlow()
            ACTION_VALIDATE_PIN -> initializeValidatePinFlow()
            ACTION_CREATE_PIN -> {
                if (isPortrait(this)) {
                    createToolbarNextButton()
                }
                setCreatePinResultListener()
                findNavController().navigate(R.id.action_start_to_create_pin)
            }
            else -> finish()
        }
    }

    private fun initializeValidatePinFlow() {
        setValidatePinResultListener()
        findNavController().navigate(R.id.action_start_to_validate_pin)
    }

    private fun initializeAppUnlockFlow() {
        initializeValidatePinFlow()
        lifecycleScope.launch {
            metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_UNLOCK_REQUESTED, getPackageUid())
        }
    }

    private fun createToolbarNextButton() {
        val menuButton =
            MenuItem.builder(this).setTitle(R.string.pin_screen_next_button_label).build()
        requireToolbar(this).setMenuItems(listOf(menuButton))
    }

    private fun setCreatePinResultListener() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.childFragmentManager.setFragmentResultListener(
            USER_PIN_REQUEST_KEY,
            this,
        ) { _, bundle ->
            logger.d("User PIN request result received!")

            val userPin = bundle.getString(USER_PIN_BUNDLE_KEY)
            if (userPin.isNullOrEmpty()) {
                logger.e("Tried to save empty PIN, aborting.")
                finish()
                return@setFragmentResultListener
            }

            lifecycleScope.launch {
                viewModel.savePin(userPin).also { status ->
                    if (!status) {
                        logger.e("Failed to save PIN, aborting.")
                        finish()
                        return@launch
                    }
                }
                metricsLogger.logAppLockEvent(AppLockEvent.APP_LOCK_ENABLED)
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    private fun setValidatePinResultListener() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragment.childFragmentManager.setFragmentResultListener(
            VALIDATE_PIN_REQUEST_KEY,
            this,
        ) { _, _ ->
            logger.d("Validate PIN request result received with valid PIN!")

            val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
            if (packageName == null) {
                logger.d("Called from Settings. Unlocking Settings.")
                lifecycleScope.launch {
                    viewModel.unlockSettings()
                    setResult(RESULT_OK)
                    finish()
                }
                return@setFragmentResultListener
            }

            logger.d("Called from Suspend Dialog. Unlocking apps and launching $packageName.")
            lifecycleScope.launch {
                viewModel.unlockApps()
                startActivity(viewModel.getLaunchIntentForPackage(packageManager, packageName))
                metricsLogger.logAppLockEvent(AppLockEvent.PACKAGE_LAUNCHED, getPackageUid())
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            lifecycleScope.launch { viewModel.setCanceledIfNotValid() }
        }
    }

    private fun findNavController(): NavController {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return navHostFragment.navController
    }

    private suspend fun getPackageUid(): Int {
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)
        return viewModel.getLockedApps().find { it.packageName == packageName }?.packageUid
            ?: NULL_PACKAGE_UID
    }

    companion object {
        private val logger = Logger(PinLockActivity::class.java)

        const val USER_PIN_REQUEST_KEY = "user_pin_request_key"
        const val USER_PIN_BUNDLE_KEY = "user_pin_bundle_key"
        const val VALIDATE_PIN_REQUEST_KEY = "validate_pin_request_key"
        const val ACTION_CREATE_PIN = "com.android.car.sensitiveapplock.action.CREATE_PIN"
        const val ACTION_VALIDATE_PIN = "com.android.car.sensitiveapplock.action.VALIDATE_PIN"
    }
}
