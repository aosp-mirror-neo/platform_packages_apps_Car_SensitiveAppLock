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
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.BackgroundContextModule
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

@UninstallModules(BackgroundContextModule::class)
@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowPackageManager::class])
@OptIn(ExperimentalCoroutinesApi::class)
class BootReceiverTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    private lateinit var receiver: BootReceiver

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val mainTestDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    // BootReceiver will now bind to the mainTestDispatcher which can be controlled.
    @BindValue @BackgroundContext val backgroundContext: CoroutineContext = mainTestDispatcher

    @Before
    fun setUp() {
        hiltRule.inject()

        Dispatchers.setMain(mainTestDispatcher)
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
    fun onReceive_shouldSuspendUserLockedApps() = runTest {
        appLockDataRepository.addLockedApp(PACKAGE_INFO.packageName)

        sendBroadcast()

        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isTrue()
    }

    private fun sendBroadcast() {
        Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED).also { intent ->
            context.sendBroadcast(intent)
        }
        // Wait for all main thread operations to complete
        Robolectric.flushForegroundThreadScheduler()
    }

    private companion object {
        val PACKAGE_INFO = PackageInfo().apply { packageName = "com.test.package" }
    }
}
