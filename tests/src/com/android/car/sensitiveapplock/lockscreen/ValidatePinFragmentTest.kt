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
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.metrics.RecoveryEvent
import com.android.car.sensitiveapplock.shadows.ShadowActivityManager
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.DEFAULT_FLAGS
import com.android.car.sensitiveapplock.testing.FakeActivityResultRegistry
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.getAppLockAtoms
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.android.car.ui.core.CarUiInstaller
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@Config(shadows = [ShadowActivityManager::class])
@OptIn(ExperimentalCoroutinesApi::class)
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

        ShadowActivityManager.reset()

        CarUiInstaller.register(context)
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
    fun onRecoveryButtonClick_whenRecoveryAccountSet_launchesReAuthActivity() = runTest {
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
    fun onRecoveryButtonClick_whenRecoveryAccountSet_logsMetrics() = runTest {
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
        }

        val atoms = getAppLockAtoms()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            recoveryEvent = RecoveryEvent.USER_STARTED_REAUTH_RECOVERY_FLOW,
        )
    }

    @Test
    fun pinRecovery_reAuthSuccessful_launchesPinLockActivity() = runTest {
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
    fun pinRecovery_reAuthSuccessful_logsMetrics() = runTest {
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

        val atoms = getAppLockAtoms().toMutableList()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.removeLastOrNull()!!,
            recoveryEvent = RecoveryEvent.USER_STARTED_PIN_RECREATE_FLOW,
        )
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            recoveryEvent = RecoveryEvent.USER_COMPLETED_REAUTH_RECOVERY_FLOW,
        )
    }

    @Test
    fun pinRecovery_reAuthSuccessful_pinRecreated_setsFragmentResult() = runTest {
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
    fun pinRecovery_reAuthSuccessful_pinRecreated_logsMetrics() = runTest {
        val testAccount = Account("test", accountType)
        shadowAccountManager.addAccount(testAccount)
        appLockDataRepository.setReAuthPinRecoveryAccount(testAccount)
        val onConfirmCredentialsBundle =
            Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true) }
        fakeActivityResultRegistry.setResult(RESULT_OK, onConfirmCredentialsBundle) // ReAuth
        fakeActivityResultRegistry.setResult(RESULT_OK) // PinRecreate

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                recoveryEvent = RecoveryEvent.USER_RECREATED_PIN,
            )
        }
    }

    @Test
    fun pinRecovery_noRecoveryAccount_showsSecurityResetDialog() {
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            assertThat(dialog.isShowing).isTrue()
            assertThat(shadowDialog.title)
                .isEqualTo(context.resources.getString(R.string.reset_dialog_title))
        }
    }

    @Test
    fun pinRecovery_noRecoveryAccount_logsMetrics() {
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                recoveryEvent = RecoveryEvent.USER_STARTED_MANUAL_RESET_RECOVERY_FLOW,
            )
        }
    }

    @Test
    fun securityResetDialog_onPrimaryCta_showClearDataDialog() = runTest {
        AppInstallationHelper.addAppToPackageManager(context, TEST_PACKAGE_NAMES[0])
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])

        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            // Confirm the reset dialog
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()
        }

        val clearDataDialog = ShadowAlertDialog.getLatestAlertDialog()
        val shadowClearDataDialog = shadowOf(clearDataDialog)
        assertThat(clearDataDialog.isShowing).isTrue()
        assertThat(shadowClearDataDialog.title)
            .isEqualTo(context.resources.getString(R.string.clear_data_dialog_title))
    }

    @Test
    fun clearDataDialog_onDismiss_showsSecurityResetDialog() = runTest {
        AppInstallationHelper.addAppToPackageManager(
            context,
            packageName = TEST_PACKAGE_NAMES[0],
            flags = ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
        )
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])
        launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
            val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

            recoverKey.performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            // Security reset dialog
            var dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            // Clear data dialog
            val clearDataDialog = ShadowAlertDialog.getLatestAlertDialog()
            clearDataDialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            assertThat(dialog.isShowing).isTrue()
            assertThat(shadowDialog.title)
                .isEqualTo(context.resources.getString(R.string.reset_dialog_title))
        }
    }

    @Test
    fun clearDataDialog_onPrimaryCta_clearsDataAndLogsMetricsAndLaunchesPinRecreateFlow() =
        runTest {
            AppInstallationHelper.addAppToPackageManager(
                context,
                packageName = TEST_PACKAGE_NAMES[0],
                flags = ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
            )
            AppInstallationHelper.addAppToPackageManager(
                context,
                packageName = TEST_PACKAGE_NAMES[1],
            )
            appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])
            appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[1])
            launchFragmentInHiltContainer<ValidatePinFragment> { fragment ->
                val recoverKey = fragment.requireView().findViewById<TextView>(R.id.button_recovery)

                recoverKey.performClick()
                shadowOf(Looper.getMainLooper()).runToEndOfTasks()

                // Security reset dialog
                val dialog = ShadowAlertDialog.getLatestAlertDialog()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).runToEndOfTasks()

                // Clear data dialog
                val clearDataDialog = ShadowAlertDialog.getLatestAlertDialog()
                clearDataDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).runToEndOfTasks()

                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val shadowAm = shadowOf(am) as ShadowActivityManager
                assertThat(shadowAm.getClearedApplicationUserDataPackages())
                    .containsExactly(TEST_PACKAGE_NAMES[0], TEST_PACKAGE_NAMES[1])

                val atoms = getAppLockAtoms()
                assertSensitiveAppLockEventAtom(
                    statsLogItem = atoms.last(),
                    recoveryEvent = RecoveryEvent.USER_STARTED_PIN_RECREATE_FLOW,
                )

                val launchedIntent = fakeActivityResultRegistry.getLastLaunchedIntent()
                assertThat(launchedIntent?.component)
                    .isEqualTo(ComponentName(context, PinLockActivity::class.java))
                assertThat(launchedIntent?.action).isEqualTo(PinLockActivity.ACTION_CREATE_PIN)
            }
        }

    private companion object {
        const val USER_PIN = "0000"

        val TEST_PACKAGE_NAMES = listOf("com.test.package.user", "com.test.package.user.two")
    }
}
