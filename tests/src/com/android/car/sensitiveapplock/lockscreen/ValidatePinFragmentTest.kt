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

import android.Manifest.permission.SUSPEND_APPS
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.ComponentName
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.os.Looper
import android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.setFragmentResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.lockscreen.PinResetDialogFragment.Companion.PIN_RESET_DIALOG_BUNDLE_KEY
import com.android.car.sensitiveapplock.lockscreen.PinResetDialogFragment.Companion.PIN_RESET_DIALOG_REQUEST_KEY
import com.android.car.sensitiveapplock.shadows.ShadowAccountManager
import com.android.car.sensitiveapplock.shadows.ShadowResources
import com.android.car.sensitiveapplock.testing.FakeActivityResultRegistry
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.android.car.ui.core.CarUiInstaller
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowResources::class, ShadowAccountManager::class])
class ValidatePinFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val hiltTestActivityRule = HiltTestActivityRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val accountType = context.resources.getString(R.string.config_recoveryAccountType)
    private val shadowAccountManager =
        shadowOf(AccountManager.get(context)).apply { addAuthenticator(accountType) }

    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var fakeActivityResultRegistry: FakeActivityResultRegistry
    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    @Before
    fun init() {
        hiltRule.inject()

        shadowOf(context).grantPermissions(SUSPEND_APPS)

        ShadowResources.reset()
    }

    @Test
    fun onViewCreated_titlesSet() {
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val title = fragment.requireView().findViewById<TextView>(R.id.title).text.toString()

            assertThat(title).isEqualTo(context.getString(R.string.validate_pin_title))
        }
    }

    @Test
    fun onEnterKeyClick_pinIsValid_setsFragmentResult() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // 4 digit pin before hitting enter
            for (i in 0..3) {
                pinPadZeroKey.performClick()
            }
            pinPadEnterKey.performClick()

            assertThat(receivedBundle).isNotNull()
        }
    }

    @Test
    fun onEnterKeyClick_pinIsValid_clearsPinResetData() = runTest {
        pinManager.setAppLockPin(USER_PIN)
        appLockDataRepository.addLockedDataClearedSystemApp("com.test")

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // 4 digit pin before hitting enter
            for (i in 0..3) {
                pinPadZeroKey.performClick()
            }
            pinPadEnterKey.performClick()
        }

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun onEnterKeyClick_pinIsNotValid_doesNotSetFragmentResult() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            assertThat(receivedBundle).isNull()
        }
    }

    @Test
    fun onEnterKeyClick_pinIsValid_recoveryDisabled_setsFragmentResult() = runTest {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, false)
        pinManager.setAppLockPin(USER_PIN)

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            // 4 digit pin before hitting enter
            for (i in 0..3) {
                pinPadZeroKey.performClick()
            }
            pinPadEnterKey.performClick()

            assertThat(receivedBundle).isNotNull()
        }
    }

    @Test
    fun onRecoveryButtonClick_whenRecoveryAccountSet_launchesReAuthActivity() = runTest {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
        }
        val launchedIntent = fakeActivityResultRegistry.getLastLaunchedIntent()
        assertThat(launchedIntent).isNotNull()
        assertThat(launchedIntent?.action).isNull()
    }

    @Test
    fun pinRecovery_ReAuthSuccessful_launchesPinLockActivity() = runTest {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)
        val onConfirmCredentialsBundle =
            Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }
        fakeActivityResultRegistry.setResult(RESULT_OK, onConfirmCredentialsBundle)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
        }

        val launchedIntent = fakeActivityResultRegistry.getLastLaunchedIntent()
        assertThat(launchedIntent?.component)
            .isEqualTo(ComponentName(context, PinLockActivity::class.java))
        assertThat(launchedIntent?.action).isEqualTo(PinLockActivity.ACTION_CREATE_PIN)
    }

    @Test
    fun pinRecovery_ReAuthSuccessful_pinRecreated_setsFragmentResult() = runTest {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        CarUiInstaller.register(context)
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)
        val onConfirmCredentialsBundle =
            Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }
        fakeActivityResultRegistry.setResult(RESULT_OK, onConfirmCredentialsBundle) // ReAuth
        fakeActivityResultRegistry.setResult(RESULT_OK) // PinRecreate

        var receivedBundle: Bundle? = null
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.VALIDATE_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                receivedBundle = bundle
            }
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()

            assertThat(receivedBundle).isNotNull()
        }
    }

    @Test
    fun pinRecovery_pinRecreated_clearsPinResetData() = runTest {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        CarUiInstaller.register(context)
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)
        val onConfirmCredentialsBundle =
            Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }
        fakeActivityResultRegistry.setResult(RESULT_OK, onConfirmCredentialsBundle) // ReAuth
        fakeActivityResultRegistry.setResult(RESULT_OK) // PinRecreate
        appLockDataRepository.addLockedDataClearedSystemApp("com.test")

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
        }

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun pinRecovery_noRecoveryAccount_showsPinResetDialog() {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        CarUiInstaller.register(context)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            val title = shadowDialog.view.findViewById<TextView>(R.id.reset_dialog_title)
            assertThat(dialog.isShowing).isTrue()
            assertThat(title.text)
                .isEqualTo(context.getString(R.string.reset_dialog_reset_now_title))
        }
    }

    @Test
    fun pinResetDialog_onResultBundleValueTrue_launchesPinLockActivity() {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        CarUiInstaller.register(context)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)
            val bundle = Bundle().apply { putBoolean(PIN_RESET_DIALOG_BUNDLE_KEY, true) }

            recoverKey.performClick()
            fragment.setFragmentResult(PIN_RESET_DIALOG_REQUEST_KEY, bundle)

            val launchedIntent = fakeActivityResultRegistry.getLastLaunchedIntent()
            assertThat(launchedIntent?.component)
                .isEqualTo(ComponentName(context, PinLockActivity::class.java))
            assertThat(launchedIntent?.action).isEqualTo(PinLockActivity.ACTION_CREATE_PIN)
        }
    }

    @Test
    fun pinResetDialog_onResultBundleValueFalse_launchesManageAllAppsActivity() {
        ShadowResources.setBoolean(R.bool.config_enablePinLockRecovery, true)
        CarUiInstaller.register(context)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)
            val bundle = Bundle().apply { putBoolean(PIN_RESET_DIALOG_BUNDLE_KEY, false) }

            recoverKey.performClick()
            fragment.setFragmentResult(PIN_RESET_DIALOG_REQUEST_KEY, bundle)

            val launchedIntent = shadowOf(context).peekNextStartedActivity()
            assertThat(launchedIntent.action).isEqualTo(ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
            assertThat(launchedIntent.flags)
                .isEqualTo(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private companion object {
        const val USER_PIN = "0000"
    }
}
