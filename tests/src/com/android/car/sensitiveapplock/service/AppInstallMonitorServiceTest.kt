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

package com.android.car.sensitiveapplock.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.suspension.AppSuspensionManager
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppInstallMonitorServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext
    @Inject lateinit var appSuspensionManager: AppSuspensionManager
    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadowPackageManager = shadowOf(context.packageManager)

    private lateinit var appInstallMonitorService: AppInstallMonitorService

    @Before
    fun setup() {
        hiltRule.inject()

        appInstallMonitorService =
            AppInstallMonitorService(
                context,
                appSuspensionManager,
                appLockDataRepository,
                backgroundContext,
            )
        appInstallMonitorService.start()

        AppInstallationHelper.addAppToPackageManager(context, TEST_PACKAGE_NAME)
    }

    @After
    fun cleanup() {
        appInstallMonitorService.stop()
    }

    @Test
    fun onReceive_packageAdded_previouslyLocked_relocksPackage() = runTest {
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAME)

        appInstallMonitorService.onReceive(context, BROADCAST_INTENT)

        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME).isSuspended).isTrue()
    }

    @Test
    fun onReceive_packageAdded_notPreviouslyLocked_doesNotRelockPackage() {
        appInstallMonitorService.onReceive(context, BROADCAST_INTENT)

        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAME).isSuspended).isFalse()
    }

    private companion object {
        const val TEST_PACKAGE_NAME = "com.package.ok"

        val BROADCAST_INTENT =
            Intent(Intent.ACTION_PACKAGE_ADDED).setData(Uri.parse("package:$TEST_PACKAGE_NAME"))
    }
}
