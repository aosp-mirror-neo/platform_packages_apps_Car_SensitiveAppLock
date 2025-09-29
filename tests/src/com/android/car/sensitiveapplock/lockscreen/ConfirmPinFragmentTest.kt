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

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.lockscreen.ConfirmPinFragment.Companion.USER_SIGNED_IN_BUNDLE_KEY
import com.android.car.sensitiveapplock.metrics.SignInEvent
import com.android.car.sensitiveapplock.testing.FakeActivityResultRegistry
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.getAppLockAtoms
import com.android.car.sensitiveapplock.testing.launchFragmentInHiltContainer
import com.android.car.ui.core.CarUi.requireToolbar
import com.android.car.ui.core.CarUiInstaller
import com.android.car.ui.toolbar.MenuItem
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmPinFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val hiltTestActivityRule = HiltTestActivityRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Inject lateinit var fakeActivityResultRegistry: FakeActivityResultRegistry

    private val accountType = context.resources.getString(R.string.config_recoveryAccountType)
    private val shadowAccountManager =
        shadowOf(AccountManager.get(context)).apply { addAuthenticator(accountType) }

    @Before
    fun init() {
        CarUiInstaller.register(context)
        hiltRule.inject()
    }

    @Test
    fun onViewCreated_titlesSet() {
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val title = fragment.requireView().findViewById<TextView>(R.id.title).text.toString()
            val subtitle =
                fragment.requireView().findViewById<TextView>(R.id.subtitle).text.toString()

            assertThat(title).isEqualTo(context.getString(R.string.confirm_pin_title))
            assertThat(subtitle).isEqualTo(context.getString(R.string.confirm_pin_subtitle))
        }
    }

    @Test
    fun onEnterKeyClick_pinMatches_userSignedIn_setsFragmentResult() {
        shadowAccountManager.addAccount(Account("com.test", accountType))
        var resultBundle: Bundle? = null
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.USER_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                resultBundle = bundle
            }
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            assertThat(resultBundle).isNotNull()
            assertThat(resultBundle?.getString(PinLockActivity.USER_PIN_BUNDLE_KEY)).isNotNull()
            assertThat(resultBundle?.getBoolean(USER_SIGNED_IN_BUNDLE_KEY)).isTrue()
        }
    }

    @Test
    fun onEnterKeyClick_pinMatches_userSignedIn_logsMetrics() {
        shadowAccountManager.addAccount(Account("com.test", accountType))
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                signInEvent = SignInEvent.USER_ALREADY_SIGNED_IN,
            )
        }
    }

    @Test
    fun onEnterKeyClick_pinMatches_userNotSignedIn_showsSignInDialog() {
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            assertThat(dialog.isShowing).isTrue()
            assertThat(shadowDialog.title)
                .isEqualTo(context.resources.getString(R.string.signin_dialog_title))
        }
    }

    @Test
    fun signInDialog_onDismiss_setsFragmentResult() {
        var actualResult: String? = null
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.USER_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                actualResult = bundle.getString(PinLockActivity.USER_PIN_BUNDLE_KEY)
            }
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            assertThat(actualResult).isNotNull()
        }
    }

    @Test
    fun signInDialog_onDismiss_logsMetrics() {
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                signInEvent = SignInEvent.USER_DECLINED_SIGN_IN,
            )
        }
    }

    @Test
    fun signInDialog_onSignIn_launchesSignInActivity() {
        shadowOf(AccountManager.get(context)).addAuthenticator("com.google")
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val launchedIntent = fakeActivityResultRegistry.getLastLaunchedIntent()
            assertThat(launchedIntent).isNotNull()
        }
    }

    @Test
    fun signInDialog_onSignIn_logsMetrics() {
        shadowOf(AccountManager.get(context)).addAuthenticator("com.google")
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                signInEvent = SignInEvent.USER_STARTED_SIGN_IN,
            )
        }
    }

    @Test
    fun signInActivity_onResult_setsFragmentResult() {
        shadowOf(AccountManager.get(context)).addAuthenticator("com.google")
        var resultBundle = Bundle.EMPTY
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.USER_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { _, bundle ->
                resultBundle = bundle
            }
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()

            // Simulate adding account by sign-in activity
            shadowAccountManager.addAccount(Account("test", accountType))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            assertThat(resultBundle.getBoolean(USER_SIGNED_IN_BUNDLE_KEY)).isTrue()
        }
    }

    @Test
    fun signInActivity_onSignInSuccess_logsMetrics() {
        shadowOf(AccountManager.get(context)).addAuthenticator("com.google")
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadEnterKey.performClick()

            val dialog = ShadowAlertDialog.getLatestAlertDialog()

            // Simulate adding account by sign-in activity
            shadowAccountManager.addAccount(Account("test", accountType))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                signInEvent = SignInEvent.USER_COMPLETED_SIGN_IN,
            )
        }
    }

    @Test
    fun onEnterKeyClick_pinDoesNotMatch_doesNotSetFragmentResult() {
        var actualResult: String? = null
        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            fragment.parentFragmentManager.setFragmentResultListener(
                PinLockActivity.USER_PIN_REQUEST_KEY,
                fragment.requireActivity(),
            ) { requestKey, bundle ->
                actualResult = bundle.getString(PinLockActivity.USER_PIN_BUNDLE_KEY)
            }
            val pinPadZeroKey = fragment.requireView().findViewById<TextView>(R.id.key_0)
            val pinPadEnterKey = fragment.requireView().findViewById<ImageButton>(R.id.key_confirm)

            pinPadZeroKey
                .performClick() // Viewmodel PIN is empty so adding digit will make it not match
            pinPadEnterKey.performClick()

            assertThat(actualResult).isNull()
        }
    }

    @Test
    fun onBackPressed_navigatesToCreatePinScreen() {
        val navController =
            TestNavHostController(context).apply {
                setGraph(R.navigation.pin_nav_graph)
                setCurrentDestination(R.id.confirm_pin)
            }

        launchFragmentInHiltContainer<ConfirmPinFragment>(
            onActivity = { activity -> setupToolbar(activity) }
        ) { fragment ->
            Navigation.setViewNavController(fragment.requireView(), navController)

            fragment.requireActivity().onBackPressedDispatcher.onBackPressed()

            assertThat(fragment.findNavController().currentDestination?.id)
                .isEqualTo(R.id.create_pin)
        }
    }

    private fun setupToolbar(activity: Activity) {
        val menuButton =
            MenuItem.builder(context).setTitle(R.string.pin_screen_next_button_label).build()
        requireToolbar(activity).setMenuItems(listOf(menuButton))
    }
}
