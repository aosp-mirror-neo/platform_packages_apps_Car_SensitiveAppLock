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
package com.android.car.sensitiveapplock.util

import android.app.Application
import android.content.pm.PackageInfo
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class AppSuspensionManagerTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val shadowMediaSessionManager =
        shadowOf(context.getSystemService(MediaSessionManager::class.java))

    @Inject lateinit var appSuspensionManager: AppSuspensionManager

    @Before
    fun init() {
        hiltRule.inject()

        shadowPackageManager.installPackage(PACKAGE_INFO)
    }

    @Test
    fun suspendApp_stateTrue_suspendsApp() {
        appSuspensionManager.setAppSuspensionState(PACKAGE_INFO.packageName, true)

        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isTrue()
    }

    @Test
    fun suspendApp_stateFalse_unsuspendsApp() {
        appSuspensionManager.setAppSuspensionState(PACKAGE_INFO.packageName, true)

        appSuspensionManager.setAppSuspensionState(PACKAGE_INFO.packageName, false)

        assertThat(shadowPackageManager.getPackageSetting(PACKAGE_INFO.packageName).isSuspended)
            .isFalse()
    }

    @Test
    fun suspendApp_whenUnsuspendingMediaApps_doesNotPauseMediaSession() {
        val mediaController = addMediaController(MEDIA_PACKAGE_INFO)
        val shadowTransportControls = shadowOf(mediaController.transportControls)
        shadowPackageManager.installPackage(MEDIA_PACKAGE_INFO)

        mediaController.transportControls.stop()
        appSuspensionManager.setAppSuspensionState(MEDIA_PACKAGE_INFO.packageName, false)

        assertThat(shadowTransportControls.lastPerformedAction)
            .isEqualTo(PlaybackState.ACTION_STOP)
    }

    @Test
    fun suspendApp_whenSuspendingMediaApps_pausesMediaSession() {
        val mediaController = addMediaController(MEDIA_PACKAGE_INFO)
        val shadowTransportControls = shadowOf(mediaController.transportControls)
        shadowPackageManager.installPackage(MEDIA_PACKAGE_INFO)

        mediaController.transportControls.play()
        appSuspensionManager.setAppSuspensionState(MEDIA_PACKAGE_INFO.packageName, true)

        assertThat(shadowTransportControls.lastPerformedAction)
            .isEqualTo(PlaybackState.ACTION_PAUSE)
    }

    private fun addMediaController(packageInfo: PackageInfo): MediaController {
        val mediaController = MediaController(context, MediaSession(context, "tag").sessionToken)
        shadowOf(mediaController).apply { setPackageName(packageInfo.packageName) }
        shadowMediaSessionManager.addController(mediaController)
        return mediaController
    }

    private companion object {
        val PACKAGE_INFO = PackageInfo().apply { packageName = "com.test.package" }
        val MEDIA_PACKAGE_INFO = PackageInfo().apply { packageName = "com.test.media.package" }
    }
}
