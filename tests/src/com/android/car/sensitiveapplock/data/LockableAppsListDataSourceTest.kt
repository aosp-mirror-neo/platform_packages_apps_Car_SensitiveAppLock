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
package com.android.car.sensitiveapplock.data

import android.Manifest.permission.SUSPEND_APPS
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.shadows.ShadowResources
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.addAppToPackageManager
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.addMediaAppToPackageManager
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

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowResources::class])
class LockableAppsListDataSourceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowPackageManager = shadowOf(context.packageManager)

    @Inject lateinit var lockableAppsListDataSource: LockableAppsListDataSource

    @Before
    fun init() {
        hiltRule.inject()
        shadowOf(context).grantPermissions(SUSPEND_APPS)
        ShadowResources.reset()
    }

    @Test
    fun getLockableApps_containsOnlyLockableApps() = runTest {
        addLauncherActivities()
        val lockableApps = lockableAppsListDataSource.getLockableApps().map { it.packageName }

        val unsuspendablePackages =
            context.resources.getStringArray(R.array.unsuspendable_packages) +
                TEST_MAPS_PACKAGE +
                TEST_ASSISTANT_PACKAGE +
                TEST_MEDIA_DISALLOW_DATA_CLEARING_PACKAGE +
                TEST_DISALLOW_DATA_CLEARING_PACKAGE

        assertThat(lockableApps).isNotEmpty()
        assertThat(lockableApps).containsNoneIn(unsuspendablePackages)
    }

    @Test
    fun getLockableApps_whenMediaAppsLockingEnabled_containsMediaApps() = runTest {
        ShadowResources.setBoolean(R.bool.config_enableMediaAppsLocking, true)
        addMediaLauncherActivities()

        val lockableApps = lockableAppsListDataSource.getLockableApps()

        assertThat(lockableApps).hasSize(2)
    }

    @Test
    fun getLockableApps_whenMediaAppsLockingDisabled_doesNotContainMediaApps() = runTest {
        ShadowResources.setBoolean(R.bool.config_enableMediaAppsLocking, false)
        addMediaLauncherActivities()

        val lockableApps = lockableAppsListDataSource.getLockableApps()

        assertThat(lockableApps).isEmpty()
    }

    private fun addLauncherActivities() {
        val unsuspendablePackages = context.resources.getStringArray(R.array.unsuspendable_packages)
        val packages = TEST_PACKAGE_NAMES.toMutableList().also { it.addAll(unsuspendablePackages) }

        for (packageName in packages) {
            addAppToPackageManager(context, packageName)
        }
        addAssistantApp(TEST_ASSISTANT_PACKAGE)
        addMapsApp(TEST_MAPS_PACKAGE)
        addAppsThatDisallowDataClearing()
    }

    private fun addMediaLauncherActivities() {
        for (mediaPackage in TEST_MEDIA_PACKAGES) {
            addMediaAppToPackageManager(context, mediaPackage)
        }
    }

    private fun addAssistantApp(packageName: String) {
        val intentFilter = IntentFilter(Intent.ACTION_VOICE_ASSIST)
        addAppToPackageManager(context, packageName, intentFilter)
    }

    private fun addMapsApp(packageName: String) {
        val intentFilter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MAPS) }
        addAppToPackageManager(context, packageName, intentFilter)
    }

    private fun addAppsThatDisallowDataClearing() {
        addAppToPackageManager(
            context,
            TEST_DISALLOW_DATA_CLEARING_PACKAGE,
            flags = ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA.inv(),
        )
        addAppToPackageManager(
            context,
            TEST_MEDIA_DISALLOW_DATA_CLEARING_PACKAGE,
            flags = ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA.inv(),
        )
    }

    private companion object {
        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")
        val TEST_MEDIA_PACKAGES = listOf("com.package.media.1", "com.package.media.2")

        const val TEST_MAPS_PACKAGE = "com.package.maps"
        const val TEST_ASSISTANT_PACKAGE = "com.package.assistant"
        const val TEST_MEDIA_DISALLOW_DATA_CLEARING_PACKAGE =
            "com.package.media.disallow.data.clearing"
        const val TEST_DISALLOW_DATA_CLEARING_PACKAGE = "com.package.disallow.data.clearing"
    }
}
