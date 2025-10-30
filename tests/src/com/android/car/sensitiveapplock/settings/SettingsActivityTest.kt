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

import android.Manifest.permission.SUSPEND_APPS
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.UserManager
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.lockscreen.PinLockActivity
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.notification.NotificationInteractionService
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.getAppLockAtoms
import com.android.car.ui.core.CarUiInstaller
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowUserManager

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowApplication = shadowOf(context as Application)
    private val shadowUserManager =
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).apply {
            setSupportsMultipleUsers(true)
            addUser(
                ShadowUserManager.DEFAULT_SECONDARY_USER_ID,
                "guest_user",
                ShadowUserManager.FLAG_GUEST,
            )
        }

    private lateinit var activityScenario: ActivityScenario<SettingsActivity>

    @Inject lateinit var pinManager: PinManager

    @Inject lateinit var settingsLockManager: SettingsLockManager

    @Before
    fun init() {
        hiltRule.inject()

        shadowOf(context).grantPermissions(SUSPEND_APPS)

        // Enable CarUiInstaller so car-ui-toolbar can be installed
        CarUiInstaller.register(context)

        // All tests assume PIN is set at start
        runBlocking { pinManager.setAppLockPin(USER_PIN) }

        activityScenario = ActivityScenario.launch(SettingsActivity::class.java)
    }

    @After
    fun tearDown() {
        activityScenario.close()
    }

    @Test
    fun onCreate_onGuestUser_showsToastAndFinishesActivity() {
        shadowUserManager.switchUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID)

        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            assertThat(ShadowToast.getTextOfLatestToast())
                .isEqualTo(context.getString(R.string.guest_user_exit_toast_message))
            assertThat(scenario.state).isEqualTo(Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun onCreate_pinSet_doesNotInitializeUi() = runTest {
        activityScenario.onActivity { activity ->
            val fragments = activity.supportFragmentManager.fragments
            assertThat(fragments.size).isEqualTo(0)
        }
    }

    @Test
    fun onCreate_pinSet_startsPinLockActivity() = runTest {
        activityScenario.onActivity { activity ->
            val launchedIntent = shadowOf(activity).peekNextStartedActivity()
            assertThat(launchedIntent.action).isEqualTo(PinLockActivity.ACTION_VALIDATE_PIN)
            assertThat(launchedIntent.flags)
                .isEqualTo(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    @Test
    fun onCreate_pinUnset_initializesUi() = runTest {
        pinManager.clearAppLockPin()
        // Activity is already created in @Before so after clearing pin we must recreate it so that
        // it reads the right value in onCreate.
        activityScenario.close()
        activityScenario = ActivityScenario.launch(SettingsActivity::class.java)

        activityScenario.onActivity { activity ->
            val fragments = activity.supportFragmentManager.fragments
            assertThat(fragments.size).isEqualTo(1)
            assertThat(fragments[0]).isInstanceOf(SettingsFragment::class.java)
        }
    }

    @Test
    fun onCreate_pinSet_hasSavedInstanceState_doesNotStartPinLockActivity() {
        activityScenario.onActivity { activity ->
            shadowOf(activity).clearNextStartedActivities()
            activity.recreate()

            val fragments = activity.supportFragmentManager.fragments
            assertThat(fragments.size).isEqualTo(0)
        }
    }

    @Test
    fun onCreate_startedFromNotification_dismissesNotification() {
        // Launch an activity scenario with an intent similar to being launched via the pending
        // intent in a notification.
        val intent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }

        // The existing activity scenario does not conflict with this because it does not have
        // the intent extra EXTRA_NOTIFICATION_ID
        val scenario = ActivityScenario.launch<SettingsActivity>(intent)

        val startedServices = shadowApplication.allStartedServices
        val notificationServices =
            startedServices.filter { intent ->
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1) == NOTIFICATION_ID &&
                    intent.component?.className ==
                        NotificationInteractionService::class.qualifiedName
            }
        assertThat(notificationServices).hasSize(1)

        scenario.close()
    }

    @Test
    fun onCreate_logsMetrics() {
        val atoms = getAppLockAtoms()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            appLockEvent = AppLockEvent.APP_LOCK_SETTINGS_SCREEN_OPENED,
        )
    }

    @Test
    fun onNewIntent_pinSet_startsPinLockActivity() {
        activityScenario.onActivity { activity ->
            shadowOf(activity).clearNextStartedActivities()

            activity.onNewIntent(Intent())

            val launchedIntent = shadowOf(activity).peekNextStartedActivity()
            assertThat(launchedIntent.action).isEqualTo(PinLockActivity.ACTION_VALIDATE_PIN)
            assertThat(launchedIntent.flags and FLAG_ACTIVITY_NEW_TASK)
                .isEqualTo(FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Test
    fun onNewIntent_pinUnset_initializesUi() = runTest {
        activityScenario.onActivity { activity ->
            shadowOf(activity).clearNextStartedActivities()
            activity.lifecycleScope.launch { pinManager.clearAppLockPin() }

            activity.onNewIntent(Intent())

            val fragments = activity.supportFragmentManager.fragments
            assertThat(fragments.size).isEqualTo(1)
            assertThat(fragments[0]).isInstanceOf(SettingsFragment::class.java)
        }
    }

    @Test
    fun onNewIntent_startedFromNotification_dismissesNotification() {
        val settingsIntent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }

        activityScenario.onActivity { activity ->
            shadowOf(activity).clearNextStartedActivities()
            activity.lifecycleScope.launch { pinManager.clearAppLockPin() }

            activity.onNewIntent(settingsIntent)

            val startedServices = shadowApplication.allStartedServices
            val notificationServices =
                startedServices.filter { intent ->
                    intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1) == NOTIFICATION_ID &&
                        intent.component?.className ==
                            NotificationInteractionService::class.qualifiedName
                }
            assertThat(notificationServices).hasSize(1)
        }
    }

    @Test
    fun onNewIntent_logsMetrics() = runTest {
        activityScenario.onActivity { activity ->
            shadowOf(activity).clearNextStartedActivities()
            activity.lifecycleScope.launch { pinManager.clearAppLockPin() }

            activity.onNewIntent(Intent())

            val atoms = getAppLockAtoms()
            assertSensitiveAppLockEventAtom(
                statsLogItem = atoms.last(),
                appLockEvent = AppLockEvent.APP_LOCK_SETTINGS_SCREEN_OPENED,
            )
        }
    }

    @Test
    fun validPinReceived_initializesUi() {
        activityScenario.onActivity { activity ->
            settingsLockManager.setLockStatus(SettingsLockStatus.VALID_PIN)

            val fragments = activity.supportFragmentManager.fragments
            assertThat(fragments.size).isEqualTo(1)
            assertThat(fragments[0]).isInstanceOf(SettingsFragment::class.java)
        }
    }

    @Test
    fun pinInputCanceled_finishesActivity() {
        activityScenario.onActivity { activity ->
            settingsLockManager.setLockStatus(SettingsLockStatus.CANCELED_PIN)

            assertThat(activity.isFinishing).isTrue()
        }
    }

    private companion object {
        const val USER_PIN = "1111"
        const val NOTIFICATION_ID = 1001
    }
}
