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
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.settings.SettingsLockManager
import com.android.car.sensitiveapplock.settings.SettingsLockStatus
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.DEFAULT_FLAGS
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.addAppToPackageManager
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.assertSensitiveAppLockEventAtom
import com.android.car.sensitiveapplock.testing.MetricsTestHelper.getAppLockAtoms
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.android.car.ui.core.CarUi
import com.android.car.ui.core.CarUiInstaller
import com.android.car.ui.toolbar.NavButtonMode
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowStatsLog

@HiltAndroidTest
@SmallTest
@RunWith(RobolectricTestParameterInjector::class)
class PinLockActivityTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var activityScenario: ActivityScenario<PinLockActivity>

    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var settingsLockManager: SettingsLockManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var appSuspensionManager: AppSuspensionManager

    @Before
    fun init() {
        shadowOf(context).grantPermissions(SUSPEND_APPS)
        hiltRule.inject()
        CarUiInstaller.register(context)
    }

    @After
    fun tearDown() {
        activityScenario.close()
    }

    @Test
    fun onCreate_actionCreatePin_isPortrait_createsToolbarNextButton() {
        shadowOf(context.packageManager).setSystemFeature(SPLITSCREEN_MULTITASKING_FEATURE, true)
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val menuItems = CarUi.requireToolbar(activity).menuItems

            assertThat(menuItems.size).isEqualTo(MENU_ITEM_COUNT)
            assertThat(menuItems.first().title)
                .isEqualTo(context.getString(R.string.pin_screen_next_button_label))
        }
    }

    @Test
    fun onCreate_actionCreatePin_isPortrait_toolbarContainsOneItem() {
        shadowOf(context.packageManager).setSystemFeature(SPLITSCREEN_MULTITASKING_FEATURE, true)
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val menuItems = CarUi.requireToolbar(activity).menuItems

            assertThat(menuItems.size).isEqualTo(MENU_ITEM_COUNT)
        }
    }

    @Test
    fun onCreate_actionCreatePin_navigatesToCreatePin() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val navController =
                (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        as NavHostFragment)
                    .navController

            assertThat(navController.currentDestination?.id).isEqualTo(R.id.create_pin)
        }
    }

    @Test
    fun onCreate_actionShowSuspendedAppDetails_navigatesToValidatePin() {
        createActivityScenarioWithAction(Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS)

        activityScenario.onActivity { activity ->
            val navController =
                (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        as NavHostFragment)
                    .navController

            assertThat(navController.currentDestination?.id).isEqualTo(R.id.validate_pin)
        }
    }

    @Test
    fun onCreate_actionShowSuspendedAppDetails_logsMetrics() = runTest {
        addAppToPackageManager(
            context,
            TEST_PACKAGE_NAMES[0],
            uid = 100,
            flags = ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
        )
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])
        val pinLockIntent =
            Intent(context, PinLockActivity::class.java).apply {
                action = Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS
                putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAMES[0])
            }

        activityScenario = ActivityScenario.launchActivityForResult(pinLockIntent)

        val atoms = getAppLockAtoms()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            packageUid = 100,
            appLockEvent = AppLockEvent.PACKAGE_UNLOCK_REQUESTED,
        )
    }

    @Test
    fun onCreate_actionValidatePin_navigatesToValidatePin() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_VALIDATE_PIN)

        activityScenario.onActivity { activity ->
            val navController =
                (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        as NavHostFragment)
                    .navController

            assertThat(navController.currentDestination?.id).isEqualTo(R.id.validate_pin)
        }
    }

    @Test
    fun onCreate_hasSavedInstanceState_doesNotNavigate(
        @TestParameter(
            PinLockActivity.ACTION_CREATE_PIN,
            PinLockActivity.ACTION_VALIDATE_PIN,
            Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS,
        )
        action: String
    ) {
        createActivityScenarioWithAction(action)

        activityScenario.onActivity { activity ->
            val navController =
                (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                        as NavHostFragment)
                    .navController
            navController.navigate(R.id.start)

            activity.recreate()

            assertThat(navController.currentDestination?.id).isEqualTo(R.id.start)
        }
    }

    @Test
    fun onCreate_noActionSet_finishesActivity() {
        createActivityScenarioWithAction("")

        assertThat(activityScenario.state).isEqualTo(Lifecycle.State.DESTROYED)
    }

    @Test
    fun onCreate_navButtonSetToBack() {
        // Any valid action is fine just to make sure the activity doesn't auto-finish
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            assertThat(CarUi.requireToolbar(activity).navButtonMode).isEqualTo(NavButtonMode.BACK)
        }
    }

    @Test
    fun onUserPinRequestResult_emptyPin_doesNotSavePin() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!

            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(PinLockActivity.USER_PIN_REQUEST_KEY, Bundle())

            runTest {
                assertThat(pinManager.getAppLockPinState()).isEqualTo(PinManager.PinState.UNSET)
            }
        }
    }

    @Test
    fun onUserPinRequestResult_savesPin() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!

            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(
                    PinLockActivity.USER_PIN_REQUEST_KEY,
                    bundleOf(PinLockActivity.USER_PIN_BUNDLE_KEY to USER_PIN),
                )

            runTest {
                assertThat(pinManager.getAppLockPinState()).isEqualTo(PinManager.PinState.SET)
            }
        }

        assertThat(activityScenario.result.resultCode).isEqualTo(RESULT_OK)
    }

    @Test
    fun onValidatePinRequestResult_calledFromSettings_unlocksSettings() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_VALIDATE_PIN)

        activityScenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!

            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(PinLockActivity.VALIDATE_PIN_REQUEST_KEY, Bundle())

            assertThat(settingsLockManager.lockStatusFlow.value)
                .isEqualTo(SettingsLockStatus.VALID_PIN)
        }

        assertThat(activityScenario.result.resultCode).isEqualTo(RESULT_OK)
    }

    @Test
    fun onValidatePinRequestResult_calledFromSuspendDialog_unlocksApp() = runTest {
        val shadowPackageManager = shadowOf(context.packageManager)
        // Need to add an intent filter to have getlaunchIntentForPackage properly resolve
        val intentFilter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        for (packageName in TEST_PACKAGE_NAMES) {
            AppInstallationHelper.addAppToPackageManager(context, packageName, intentFilter)
            appSuspensionManager.setAppSuspensionState(packageName, true)
            appLockDataRepository.addLockedApp(packageName)
        }
        createActivityScenarioWithAction(Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS)

        activityScenario.onActivity { activity ->
            // Extra comes from the calling intent but I'm adding it in post here since adding it to
            // the createActivityScenario function would mess up the calledFromSettings variant of
            // this test.
            activity.intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAMES[0])
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(PinLockActivity.VALIDATE_PIN_REQUEST_KEY, Bundle())
        }

        assertThat(activityScenario.result.resultCode).isEqualTo(RESULT_OK)

        // Only first app should be unlocked
        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[0]).isSuspended)
            .isFalse()
        TEST_PACKAGE_NAMES.drop(1)
            .all { shadowPackageManager.getPackageSetting(it).isSuspended }
            .let { allSuspended -> assertThat(allSuspended).isTrue() }
    }

    @Test
    fun onValidatePinRequestResult_calledFromSuspendDialog_logsMetrics() = runTest {
        addAppToPackageManager(
            context,
            TEST_PACKAGE_NAMES[0],
            uid = 100,
            flags = ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
        )
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])
        val pinLockIntent =
            Intent(context, PinLockActivity::class.java).apply {
                action = Intent.ACTION_SHOW_SUSPENDED_APP_DETAILS
                putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAMES[0])
            }

        activityScenario = ActivityScenario.launchActivityForResult(pinLockIntent)
        activityScenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(PinLockActivity.VALIDATE_PIN_REQUEST_KEY, Bundle())
        }

        val atoms = getAppLockAtoms()
        assertSensitiveAppLockEventAtom(
            statsLogItem = atoms.last(),
            packageUid = 100,
            appLockEvent = AppLockEvent.PACKAGE_LAUNCHED,
        )
    }

    @Test
    fun onUserPinRequestResult_logsMetric() {
        createActivityScenarioWithAction(PinLockActivity.ACTION_CREATE_PIN)

        activityScenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!

            navHostFragment.childFragmentManager.fragments
                .first()
                .setFragmentResult(
                    PinLockActivity.USER_PIN_REQUEST_KEY,
                    bundleOf(PinLockActivity.USER_PIN_BUNDLE_KEY to USER_PIN),
                )
        }

        // Pin created and feature enabled
        assertSensitiveAppLockEventAtom(
            statsLogItem = ShadowStatsLog.getStatsLogs().last(),
            appLockEvent = AppLockEvent.APP_LOCK_ENABLED,
        )
    }

    private fun createActivityScenarioWithAction(action: String) {
        val pinLockIntent = Intent(context, PinLockActivity::class.java).setAction(action)
        activityScenario = ActivityScenario.launchActivityForResult(pinLockIntent)
    }

    private companion object {
        const val SPLITSCREEN_MULTITASKING_FEATURE = "android.software.car.splitscreen_multitasking"
        const val USER_PIN = "1234"
        const val MENU_ITEM_COUNT = 1
        const val TEST_ACTIVITY_CLASS = "test.activity.class"
        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")
    }
}
