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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import android.service.media.MediaBrowserService
import com.android.car.media.common.source.MediaSource
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
class LockableAppsListDataSource
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val unsuspendablePackages =
        mutableSetOf(*context.resources.getStringArray(R.array.unsuspendable_packages))
    private val packageManager = context.packageManager
    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val logger = Logger(this.javaClass)

    /**
     * Gets a list of lockable apps on the device.
     *
     * An app is deemed to be lockable if it clears the following criteria:
     * - Not unsuspendable by Package Manager
     * - Not marked unsuspendable in the deny list in the config
     * - Not an Assistant app
     * - Not a Maps app
     */
    fun getLockableApps(): List<AppInfo> {
        val allApps = (getLauncherApps() + getMediaApps()).distinctBy { it.packageName }
        val allAppPackageNames = allApps.map(AppInfo::packageName).toTypedArray()

        unsuspendablePackages.apply {
            addAll(packageManager.getUnsuspendablePackages(allAppPackageNames))
            addAll(getAssistantApps())
            addAll(getMapsApps())
        }
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

    private fun getMediaApps(): List<AppInfo> {
        if (!context.resources.getBoolean(R.bool.config_enableMediaAppsLocking)) {
            logger.v("Media apps locking is not enabled")
            return emptyList()
        }
        return context.packageManager
            .queryIntentServices(
                Intent(MediaBrowserService.SERVICE_INTERFACE),
                PackageManager.GET_RESOLVED_FILTER,
            )
            .filter {
                val componentName = ComponentName(it.serviceInfo.packageName, it.serviceInfo.name)
                MediaSource.isAudioMediaSource(context, componentName)
            }
            .map { it.toAppInfo(context.packageManager) }
    }

    private fun getAssistantApps(): List<String> =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_VOICE_ASSIST),
                0, // flags
            )
            .map { it.activityInfo.packageName }

    private fun getMapsApps(): List<String> =
        context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS),
                0, // flags,
            )
            .map { it.activityInfo.packageName }

    private fun LauncherActivityInfo.toAppInfo(packageManager: PackageManager): AppInfo {
        return AppInfo(
            packageName = applicationInfo.packageName,
            name = applicationInfo.name ?: "",
            packageUid = applicationInfo.uid,
            label = applicationInfo.loadLabel(packageManager).toString(),
            icon = applicationInfo.loadIcon(packageManager),
            isTemplateMediaApp = false,
            isBundledApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }

    /** Converts a [ResolveInfo] to an [AppInfo]. */
    private fun ResolveInfo.toAppInfo(packageManager: PackageManager): AppInfo {
        return AppInfo(
            packageName = serviceInfo.packageName,
            name = serviceInfo.name ?: "",
            packageUid = serviceInfo.applicationInfo.uid,
            label = serviceInfo.applicationInfo.loadLabel(packageManager).toString(),
            icon = loadIcon(packageManager),
            isTemplateMediaApp = true,
            isBundledApp = (serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
        )
    }
}
