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

package com.android.car.sensitiveapplock.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValueIntoSet
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowUserManager

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PersistentBackgroundServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowUserManager =
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).apply {
            setSupportsMultipleUsers(true)
            addUser(
                ShadowUserManager.DEFAULT_SECONDARY_USER_ID,
                "guest_user",
                ShadowUserManager.FLAG_GUEST,
            )
        }

    private lateinit var serviceController: ServiceController<PersistentBackgroundService>
    private lateinit var service: PersistentBackgroundService

    @BindValueIntoSet val mockAppLockService1: AppLockService = mock<AppLockService>()
    @BindValueIntoSet val mockAppLockService2: AppLockService = mock<AppLockService>()

    @Before
    fun init() {
        hiltRule.inject()

        serviceController = Robolectric.buildService(PersistentBackgroundService::class.java)
        service = serviceController.get()
    }

    @Test
    fun onCreate_startsAllAppLockServices() {
        serviceController.create()

        verify(mockAppLockService1).start()
        verify(mockAppLockService2).start()
    }

    @Test
    fun onCreate_onGuestUser_doesNotStartServices() {
        shadowUserManager.switchUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID)

        serviceController.create()

        verify(mockAppLockService1, never()).start()
        verify(mockAppLockService2, never()).start()
    }

    @Test
    fun onBind_returnsBinder() {
        serviceController.create()
        val binder = service.onBind(Intent())

        assertThat(binder).isNotNull()
    }

    @Test
    fun onDestroy_stopsAllAppLockServices() {
        serviceController.create()
        serviceController.destroy()

        verify(mockAppLockService1).stop()
        verify(mockAppLockService2).stop()
    }

    @Test
    fun onDestroy_onGuestUser_doesNotStopServices() {
        shadowUserManager.switchUser(ShadowUserManager.DEFAULT_SECONDARY_USER_ID)

        serviceController.create()
        serviceController.destroy()

        verify(mockAppLockService1, never()).stop()
        verify(mockAppLockService2, never()).stop()
    }
}
