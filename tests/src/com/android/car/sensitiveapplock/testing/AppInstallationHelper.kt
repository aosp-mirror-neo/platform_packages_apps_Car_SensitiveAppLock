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
package com.android.car.sensitiveapplock.testing

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.os.Process
import android.service.media.MediaBrowserService
import androidx.test.core.content.pm.ApplicationInfoBuilder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

/** Object providing helper functions to install apps in testing. */
object AppInstallationHelper {
    const val DEFAULT_FLAGS = ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA
    private val DEFAULT_INTENT_FILTER =
        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

    fun buildLauncherActivityInfo(
        packageName: String,
        uid: Int? = null,
        flags: Int = DEFAULT_FLAGS,
    ): LauncherActivityInfo {
        val applicationInfo =
            ApplicationInfoBuilder.newBuilder().setPackageName(packageName).setFlags(flags).build()
        if (uid != null) {
            applicationInfo.uid = uid
        }
        val mockLauncherActivityInfo =
            mock<LauncherActivityInfo>().apply {
                whenever(getApplicationInfo()).thenReturn(applicationInfo)
            }
        return mockLauncherActivityInfo
    }

    fun addAppToPackageManager(
        context: Context,
        packageName: String,
        intentFilter: IntentFilter = DEFAULT_INTENT_FILTER,
        flags: Int = DEFAULT_FLAGS,
        uid: Int? = null,
    ) {
        val shadowLauncherApps =
            shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
        val shadowPackageManager = shadowOf(context.packageManager)
        val componentName = ComponentName(packageName, "MyActivity")

        shadowLauncherApps.addActivity(
            Process.myUserHandle(),
            buildLauncherActivityInfo(packageName, uid, flags),
        )
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(componentName, intentFilter)
    }

    fun addMediaAppToPackageManager(context: Context, packageName: String) {
        val shadowPackageManager = shadowOf(context.packageManager)
        val cmpName = ComponentName(packageName, "MediaBrowserService")
        val intentFilter = IntentFilter(MediaBrowserService.SERVICE_INTERFACE)
        val packageInfo =
            PackageInfo().apply {
                this.packageName = packageName
                this.applicationInfo =
                    ApplicationInfo().apply {
                        this.packageName = packageName
                        this.flags = ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA
                    }
            }

        // Install package first so that `addServiceIfNotPresent` will reuse the ApplicationInfo
        // from it
        shadowPackageManager.installPackage(packageInfo)
        shadowPackageManager.addServiceIfNotPresent(cmpName)
        shadowPackageManager.addIntentFilterForService(cmpName, intentFilter)
    }
}
