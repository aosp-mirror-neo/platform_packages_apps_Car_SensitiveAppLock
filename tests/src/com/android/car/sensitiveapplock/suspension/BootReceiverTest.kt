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
package com.android.car.sensitiveapplock.suspension

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.os.Looper
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
class BootReceiverTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val shadowUserManager =
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).apply {
            setSupportsMultipleUsers(true)
            addUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID, "secondary_user", 0)
        }

    private lateinit var receiver: BootReceiver

    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext

    @Before
    fun setUp() {
        hiltRule.inject()
        // BootReceiver will now bind to the mainTestDispatcher which can be controlled.
        Dispatchers.setMain(backgroundContext as CoroutineDispatcher)
        shadowPackageManager.installPackage(PACKAGE_INFO)
        receiver = BootReceiver()
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    }

    @After
    fun cleanUp() {
        context.unregisterReceiver(receiver)
        Dispatchers.resetMain()
    }

    @Test
    fun onReceive_onSecondaryUser_shouldSuspendUserLockedApps() = runTest {
        shadowUserManager.switchUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID)
        appLockDataRepository.addLockedApp(PACKAGE_INFO.packageName)

        sendBroadcast()

        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isTrue()
    }

    @Test
    fun onReceive_onSystemUser_shouldNotSuspendUserLockedApps() = runTest {
        shadowUserManager.switchUser(SYSTEM_USER_ID)
        appLockDataRepository.addLockedApp(PACKAGE_INFO.packageName)

        sendBroadcast()

        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isFalse()
    }

    private fun sendBroadcast() {
        Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).also { intent -> context.sendBroadcast(intent) }
        // Wait for all main thread operations to complete
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
    }

    private companion object {
        const val SYSTEM_USER_ID = 0

        val PACKAGE_INFO = PackageInfo().apply { packageName = "com.test.package" }
    }
}
