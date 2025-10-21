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

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.SuspendDialogInfo
import android.content.pm.SuspendDialogInfo.BUTTON_ACTION_MORE_DETAILS
import android.media.session.MediaSessionManager
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A manager for controlling app suspension state. */
@Singleton
class AppSuspensionManager @Inject constructor(@ApplicationContext applicationContext: Context) {
    private val packageManager = applicationContext.packageManager
    private val mediaSessionManager =
        applicationContext.getSystemService(MediaSessionManager::class.java)
    private val resources = applicationContext.resources

    /**
     * Sets the suspension state of an app.
     *
     * Note: The caller must have the `SUSPEND_APPS` permission.
     */
    fun setAppSuspensionState(packageName: String, state: Boolean) {
        setAppSuspensionState(arrayOf(packageName), state)
    }

    /**
     * Sets the suspension state of a list of apps.
     *
     * Note: The caller must have the `SUSPEND_APPS` permission.
     */
    fun setAppSuspensionState(packageNames: Array<String>, state: Boolean) {
        logger.d("Suspending ${packageNames.contentToString()} with state: $state")
        for (packageName in packageNames) {
            val packageLabel = getPackageLabel(packageName) ?: continue
            val suspendDialogInfo =
                SuspendDialogInfo.Builder()
                    .setTitle(resources.getString(R.string.suspend_dialog_title, packageLabel))
                    .setMessage(R.string.suspend_dialog_message)
                    .setNeutralButtonText(R.string.suspend_dialog_neutral_button_text)
                    .setNeutralButtonAction(BUTTON_ACTION_MORE_DETAILS)
                    .build()

            packageManager
                .setPackagesSuspended(
                    arrayOf(packageName),
                    state,
                    null, // appExtras
                    null, // launcherExtras
                    suspendDialogInfo,
                )
                .also { errorPackages ->
                    if (errorPackages != null && errorPackages.size > 0) {
                        logger.e("Failed to suspend: ${errorPackages.contentToString()}")
                    }
                }
        }

        // Only pause media session when packages are being suspended.
        if (state) {
            pauseMediaSessions(packageNames.toHashSet())
        }
    }

    private fun getPackageLabel(packageName: String): String? {
        try {
            val packageLabel =
                packageManager
                    .getApplicationInfo(packageName, 0)
                    .loadLabel(packageManager)
                    .toString()
            return packageLabel
        } catch (e: PackageManager.NameNotFoundException) {
            logger.d("Failed to get label since package not found: $packageName", e)
            return null
        }
    }

    private fun pauseMediaSessions(packageNames: HashSet<String>) {
        val mediaSessions = mediaSessionManager.getActiveSessions(null) // notificationListener
        for (session in mediaSessions) {
            if (packageNames.contains(session.packageName)) {
                session.transportControls.pause()
            }
        }
    }

    private companion object {
        val logger = Logger(AppSuspensionManager::class.java)
    }
}
