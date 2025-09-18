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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.service.media.MediaBrowserService
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.content.pm.ApplicationInfoBuilder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.shadows.ShadowResources
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
                TEST_ASSISTANT_PACKAGE
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
        val unsuspendableActivities =
            context.resources.getStringArray(R.array.unsuspendable_packages)
        val activities = TEST_ACTIVITIES.toMutableList().also { it.addAll(unsuspendableActivities) }

        for (activity in activities) {
            shadowLauncherApps.addActivity(
                Process.myUserHandle(),
                buildLauncherActivityInfo(activity),
            )
        }
        addAssistantApp(TEST_ASSISTANT_PACKAGE)
        addMapsApp(TEST_MAPS_PACKAGE)
    }

    private fun addMediaLauncherActivities() {
        for (mediaPackage in TEST_MEDIA_PACKAGES) {
            addMediaApps(mediaPackage)
        }
    }

    private fun addMediaApps(packageName: String) {
        val classComponentName = "MediaBrowserService"
        val cmpName = ComponentName(packageName, classComponentName)
        val intentFilter = IntentFilter(MediaBrowserService.SERVICE_INTERFACE)
        shadowPackageManager.addServiceIfNotPresent(cmpName)
        shadowPackageManager.addIntentFilterForService(cmpName, intentFilter)
    }

    private fun addAssistantApp(packageName: String) {
        val intentFilter = IntentFilter(Intent.ACTION_VOICE_ASSIST)
        addAppToPackageManager(packageName, intentFilter)
    }

    private fun addMapsApp(packageName: String) {
        val intentFilter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MAPS) }
        addAppToPackageManager(packageName, intentFilter)
    }

    private fun addAppToPackageManager(packageName: String, intentFilter: IntentFilter) {
        val componentName = ComponentName(packageName, "MyActivity")
        shadowLauncherApps.addActivity(
            Process.myUserHandle(),
            buildLauncherActivityInfo(componentName.packageName),
        )
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(componentName, intentFilter)
    }

    private fun buildLauncherActivityInfo(packageName: String): LauncherActivityInfo {
        val applicationInfo =
            ApplicationInfoBuilder.newBuilder().setPackageName(packageName).build()
        val mockLauncherActivityInfo =
            mock<LauncherActivityInfo>().apply {
                whenever(getApplicationInfo()).thenReturn(applicationInfo)
            }
        return mockLauncherActivityInfo
    }

    private companion object {
        val TEST_ACTIVITIES = listOf("com.package.1", "com.package.2")
        val TEST_MEDIA_PACKAGES = listOf("com.package.media.1", "com.package.media.2")
        const val TEST_MAPS_PACKAGE = "com.package.maps"
        const val TEST_ASSISTANT_PACKAGE = "com.package.assistant"
    }
}
