/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.sensitiveapplock.lockscreen

import android.app.AlertDialog
import android.app.Application
import android.app.Dialog
import android.graphics.Color.TRANSPARENT
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.data.AppInfo
import com.android.car.sensitiveapplock.lockscreen.PinResetDialogFragment.Companion.PIN_RESET_DIALOG_BUNDLE_KEY
import com.android.car.sensitiveapplock.lockscreen.PinResetDialogFragment.Companion.PIN_RESET_DIALOG_REQUEST_KEY
import com.android.car.sensitiveapplock.testing.HiltTestActivity
import com.android.car.sensitiveapplock.testing.HiltTestActivityRule
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class PinResetDialogFragmentTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val hiltTestActivityRule = HiltTestActivityRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private lateinit var scenario: ActivityScenario<HiltTestActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun cleanUp() {
        scenario.close()
    }

    @Test
    fun onCreate_userAndSystemAppsIsEmpty_showsResetNowDialog() {
        val lockedApps = emptyList<AppInfo>()
        val dataClearedApps = emptyList<String>()

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertResetNowDialog(fragment.dialog)
        }
    }

    @Test
    fun onCreate_userAppsEmptyAndSystemAppsNotEmpty_showsSystemAppsList() {
        val lockedApps = listOf(SYSTEM_APP_INFO)
        val dataClearedApps = emptyList<String>()

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertAppsListDialog(
                dialog = fragment.dialog,
                userAppsVisibility = View.GONE,
                systemAppsVisibility = View.VISIBLE,
            )
        }
    }

    @Test
    fun onCreate_userAppsEmpty_systemAppsClearedAndNotEmpty_showsResetNowDialog() {
        val lockedApps = listOf(SYSTEM_APP_INFO)
        val dataClearedApps = listOf(SYSTEM_APP_INFO.packageName)

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertResetNowDialog(fragment.dialog)
        }
    }

    @Test
    fun onCreate_userAppsNotEmpty_showsUserAppsList() {
        val lockedApps = listOf(USER_APP_INFO)
        val dataClearedApps = emptyList<String>()

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertAppsListDialog(
                dialog = fragment.dialog,
                userAppsVisibility = View.VISIBLE,
                systemAppsVisibility = View.GONE,
            )
        }
    }

    @Test
    fun onCreate_userAppsAndSystemAppsNotEmpty_showsUserAppsAndSystemAppsList() {
        val lockedApps = listOf(USER_APP_INFO, SYSTEM_APP_INFO)
        val dataClearedApps = emptyList<String>()

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertAppsListDialog(
                dialog = fragment.dialog,
                userAppsVisibility = View.VISIBLE,
                systemAppsVisibility = View.VISIBLE,
            )
        }
    }

    @Test
    fun onCreate_userAppsAndSystemAppsNotEmpty_systemAppsDataCleared_showsUserAppsList() {
        val lockedApps = listOf(USER_APP_INFO, SYSTEM_APP_INFO)
        val dataClearedApps = listOf(SYSTEM_APP_INFO.packageName)

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            assertAppsListDialog(
                dialog = fragment.dialog,
                userAppsVisibility = View.VISIBLE,
                systemAppsVisibility = View.GONE,
            )
        }
    }

    @Test
    fun resetNowDialog_onDismiss_setsResultTrue() {
        val lockedApps = emptyList<AppInfo>()
        val dataClearedApps = emptyList<String>()
        var resultBundle: Bundle? = null

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            fragment.setFragmentResultListener(PIN_RESET_DIALOG_REQUEST_KEY) { _, bundle ->
                resultBundle = bundle
            }

            val dialog = fragment.dialog as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            assertThat(resultBundle?.getBoolean(PIN_RESET_DIALOG_BUNDLE_KEY)).isTrue()
        }
    }

    @Test
    fun appListDialog_onDismiss_setsResultFalse() {
        val lockedApps = listOf(USER_APP_INFO, SYSTEM_APP_INFO)
        val dataClearedApps = emptyList<String>()
        var resultBundle: Bundle? = null

        launchFragment(lockedApps, dataClearedApps) { fragment ->
            fragment.setFragmentResultListener(PIN_RESET_DIALOG_REQUEST_KEY) { _, bundle ->
                resultBundle = bundle
            }

            val dialog = fragment.dialog as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            assertThat(resultBundle?.getBoolean(PIN_RESET_DIALOG_BUNDLE_KEY)).isFalse()
        }
    }

    private fun assertResetNowDialog(dialog: Dialog?) {
        val shadowDialog = shadowOf(dialog as AlertDialog)
        val title = shadowDialog.view.findViewById<TextView>(R.id.reset_dialog_title)

        assertThat(title.text)
            .isEqualTo(context.resources.getString(R.string.reset_dialog_reset_now_title))
    }

    private fun assertAppsListDialog(
        dialog: Dialog?,
        userAppsVisibility: Int,
        systemAppsVisibility: Int,
    ) {
        val shadowDialog = shadowOf(dialog as AlertDialog)
        val title = shadowDialog.view.findViewById<TextView>(R.id.reset_dialog_title)
        val userAppGrid = shadowDialog.view.findViewById<View>(R.id.reset_dialog_user_apps_list)
        val systemAppsGrid =
            shadowDialog.view.findViewById<View>(R.id.reset_dialog_user_preinstalled_apps_list)

        assertThat(title.text).isEqualTo(context.resources.getString(R.string.reset_dialog_title))
        assertThat(userAppGrid.visibility).isEqualTo(userAppsVisibility)
        assertThat(systemAppsGrid.visibility).isEqualTo(systemAppsVisibility)
    }

    private fun launchFragment(
        lockedApps: List<AppInfo>,
        dataClearedApps: List<String>,
        onFragment: (PinResetDialogFragment) -> Unit = {},
    ) {
        scenario = ActivityScenario.launch(HiltTestActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onActivity { activity ->
            val fragment = PinResetDialogFragment(lockedApps, dataClearedApps)
            fragment.showNow(activity.supportFragmentManager, "")
            onFragment(fragment)
        }
    }

    private companion object {
        val USER_APP_INFO =
            AppInfo(
                packageName = "com.test.package.user",
                name = "User App",
                packageUid = 1,
                label = "UserApp",
                icon = ColorDrawable(TRANSPARENT),
                isTemplateMediaApp = false,
                isBundledApp = false,
            )

        val SYSTEM_APP_INFO =
            AppInfo(
                packageName = "com.test.package.system",
                name = "System App",
                packageUid = 2,
                label = "SystemApp",
                icon = ColorDrawable(TRANSPARENT),
                isTemplateMediaApp = false,
                isBundledApp = true,
            )
    }
}
