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

package com.android.car.sensitiveapplock.recovery

import android.Manifest.permission.SUSPEND_APPS
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Looper
import android.os.Process
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListRepository
import com.android.car.sensitiveapplock.testing.TestHelpers
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowUserManager

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PackageClearedReceiverTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowUserManager =
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).apply {
            setSupportsMultipleUsers(true)
            addUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID, "secondary_user", 0)
        }

    private lateinit var receiver: PackageClearedReceiver

    @Inject lateinit var lockableAppsListRepository: LockableAppsListRepository
    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        shadowUserManager.switchUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID)
        installTestPackage(SYSTEM_PACKAGE, ApplicationInfo.FLAG_SYSTEM)
        installTestPackage(USER_PACKAGE, 0)
        receiver = PackageClearedReceiver()
        val intentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_DATA_CLEARED)
                addDataScheme("package")
            }
        context.registerReceiver(receiver, intentFilter)
        // Important to grant permission after switching user
        shadowOf(context).grantPermissions(SUSPEND_APPS)
    }

    @After
    fun cleanUp() {
        context.unregisterReceiver(receiver)
    }

    @Test
    fun onReceive_actionIsNotPackageDataCleared_shouldNotAddApp() = runTest {
        val intent =
            Intent().apply {
                action = Intent.ACTION_BOOT_COMPLETED
                data = android.net.Uri.parse("package:$SYSTEM_PACKAGE")
            }

        context.sendBroadcast(intent)
        // Wait for all main thread operations to complete
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun onReceive_whenPackageIsEmpty_shouldNotAddApp() = runTest {
        sendBroadcast("")

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun onReceive_whenPackageIsNotLocked_shouldNotAddApp() = runTest {
        sendBroadcast(SYSTEM_PACKAGE)

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun onReceive_whenPackageIsNotSystemApp_shouldNotAddApp() = runTest {
        appLockDataRepository.addLockedApp(USER_PACKAGE)

        sendBroadcast(USER_PACKAGE)

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun onReceive_whenPackageIsLockedSystemApp_shouldAddApp() = runTest {
        appLockDataRepository.addLockedApp(SYSTEM_PACKAGE)

        sendBroadcast(SYSTEM_PACKAGE)

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps())
            .containsExactly(SYSTEM_PACKAGE)
    }

    @Test
    fun onReceive_onSystemUser_shouldNotAddApp() = runTest {
        shadowUserManager.switchUser(SYSTEM_USER_ID)
        appLockDataRepository.addLockedApp(SYSTEM_PACKAGE)

        sendBroadcast(SYSTEM_PACKAGE)

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    private fun sendBroadcast(packageName: String) {
        val intent =
            Intent().apply {
                action = Intent.ACTION_PACKAGE_DATA_CLEARED
                data = android.net.Uri.parse("package:$packageName")
            }
        context.sendBroadcast(intent)
        // Wait for all main thread operations to complete
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
    }

    private fun installTestPackage(pkg: String, flg: Int) {
        val mockLauncherActivityInfo =
            TestHelpers.buildLauncherActivityInfo(packageName = pkg, flags = flg)
        shadowLauncherApps.addActivity(Process.myUserHandle(), mockLauncherActivityInfo)
    }

    private companion object {
        const val SYSTEM_USER_ID = 0
        const val SYSTEM_PACKAGE = "com.android.system.package"
        const val USER_PACKAGE = "com.android.user.package"
    }
}
