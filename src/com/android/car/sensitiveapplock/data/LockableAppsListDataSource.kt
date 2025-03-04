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

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data source for the list of lockable apps.
 *
 * An app is considered lockable if it meets the following requirements:
 * - Returned by [LauncherApps.getActivityList]
 * - Is not considered unsuspendable by [PackageManager]
 * - Is not explicitly filtered by [R.array.unsuspendable_packages]
 */
@SuppressLint("MissingPermission")
@Singleton
class LockableAppsListDataSource @Inject constructor(@ApplicationContext context: Context) {
    private val unsuspendablePackages =
        mutableSetOf(*context.resources.getStringArray(R.array.unsuspendable_packages))
    private val packageManager = context.packageManager
    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val logger = Logger(this.javaClass)

    fun getLockableApps(): List<AppInfo> {
        val allApps = getLauncherApps()

        val allAppPackageNames = allApps.map(AppInfo::packageName).toTypedArray()
        unsuspendablePackages.addAll(packageManager.getUnsuspendablePackages(allAppPackageNames))
        logger.d("Unsuspendable apps list: $unsuspendablePackages")

        return allApps
            .filterNot { unsuspendablePackages.contains(it.packageName) }
            .sortedBy { it.label }
    }

    private fun getLauncherApps(): List<AppInfo> {
        return launcherApps
            .getActivityList(
                null, // packageName
                Process.myUserHandle(),
            )
            .map { it.toAppInfo(packageManager) }
    }

    private fun LauncherActivityInfo.toAppInfo(packageManager: PackageManager): AppInfo {
        return AppInfo(
            packageName = applicationInfo.packageName,
            packageUid = applicationInfo.uid,
            label = applicationInfo.loadLabel(packageManager).toString(),
            icon = applicationInfo.loadIcon(packageManager),
        )
    }
}
