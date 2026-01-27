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
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.service.PackageChangeMonitor.Listener
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PackageChangeMonitorTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private lateinit var packageChangeMonitor: PackageChangeMonitor
    private val shadowContext = shadowOf(context)

    @Before
    fun setUp() {
        packageChangeMonitor = PackageChangeMonitor(context)

        shadowContext.clearRegisteredReceivers()
    }

    @Test
    fun addListener_firstListener_registersReceiver() {
        val listener = mock<Listener>()

        packageChangeMonitor.addListener(listener)

        assertThat(shadowContext.registeredReceivers).hasSize(1)
        val receiverInfo = shadowContext.registeredReceivers[0]
        assertThat(receiverInfo.intentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED)).isTrue()
        assertThat(receiverInfo.intentFilter.hasDataScheme("package")).isTrue()
        assertThat(receiverInfo.flags).isEqualTo(Context.RECEIVER_NOT_EXPORTED)
    }

    @Test
    fun addListener_multipleListeners_registersReceiverOnce() {
        val listener1 = mock<Listener>()
        val listener2 = mock<Listener>()

        packageChangeMonitor.addListener(listener1)
        packageChangeMonitor.addListener(listener2)

        assertThat(shadowContext.registeredReceivers).hasSize(1)
    }

    @Test
    fun removeListener_lastListener_unregistersReceiver() {
        val listener = mock<Listener>()
        packageChangeMonitor.addListener(listener)

        packageChangeMonitor.removeListener(listener)

        assertThat(shadowContext.registeredReceivers).isEmpty()
    }

    @Test
    fun removeListener_notLastListener_doesNotUnregisterReceiver() {
        val listener1 = mock<Listener>()
        val listener2 = mock<Listener>()
        packageChangeMonitor.addListener(listener1)
        packageChangeMonitor.addListener(listener2)

        packageChangeMonitor.removeListener(listener1)

        assertThat(shadowContext.registeredReceivers).hasSize(1)
    }

    @Test
    fun onReceive_actionPackageAdded_notifiesListeners() {
        val listener1 = mock<Listener>()
        val listener2 = mock<Listener>()
        packageChangeMonitor.addListener(listener1)
        packageChangeMonitor.addListener(listener2)

        val packageName = "com.example.test"
        val intent =
            Intent(Intent.ACTION_PACKAGE_ADDED).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(listener1).onPackageAdded(packageName, false)
        verify(listener2).onPackageAdded(packageName, false)
    }

    @Test
    fun onReceive_actionPackageAdded_onPackageReplaced_notifiesListener() {
        val listener = mock<Listener>()
        packageChangeMonitor.addListener(listener)

        val packageName = "com.example.test"
        val intent =
            Intent(Intent.ACTION_PACKAGE_ADDED).apply {
                data = Uri.fromParts("package", packageName, null)
                putExtra(Intent.EXTRA_REPLACING, true)
            }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(listener).onPackageAdded(packageName, true)
    }

    @Test
    fun onReceive_actionPackageAdded_withNullPackageName_doesNotNotifyListeners() {
        val listener = mock<Listener>()
        packageChangeMonitor.addListener(listener)

        val intent = Intent(Intent.ACTION_PACKAGE_ADDED) // No data URI
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(listener, never()).onPackageAdded(anyString(), anyBoolean())
    }

    @Test
    fun onReceive_otherAction_doesNotNotifyListeners() {
        val listener = mock<Listener>()
        packageChangeMonitor.addListener(listener)

        val packageName = "com.example.test"
        val intent =
            Intent(Intent.ACTION_PACKAGE_REMOVED).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(listener, never()).onPackageAdded(anyString(), anyBoolean())
    }
}
