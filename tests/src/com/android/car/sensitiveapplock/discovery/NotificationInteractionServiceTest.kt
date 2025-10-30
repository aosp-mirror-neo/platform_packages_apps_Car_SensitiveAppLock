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

package com.android.car.sensitiveapplock.discovery

import android.app.Notification
import android.app.NotificationManager
import android.app.Service.START_NOT_STICKY
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.notification.NotificationInteractionService
import com.android.car.sensitiveapplock.notification.NotificationService.Companion.EXTRA_NOTIFICATION_DISMISS
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationInteractionServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val shadowNotificationManager = shadowOf(notificationManager)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    private lateinit var serviceController: ServiceController<NotificationInteractionService>
    private lateinit var service: NotificationInteractionService

    @Before
    fun init() = runTest {
        hiltRule.inject()

        serviceController = Robolectric.buildService(NotificationInteractionService::class.java)
        service = serviceController.get()
        service.appLockDataRepository = appLockDataRepository
    }

    @Test
    fun onStart_withNotificationId_cancelsNotificationAndIncrementsInteractionCount() = runTest {
        val notificationId = 123
        val intent =
            Intent(context, NotificationInteractionService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }

        // Simulate a notification being active
        notificationManager.notify(notificationId, Notification.Builder(context).build())
        assertThat(shadowNotificationManager.size()).isEqualTo(1)

        val result = service.onStartCommand(intent, 0, 1)

        // Verify notification is cancelled
        assertThat(shadowNotificationManager.size()).isEqualTo(0)
        assertThat(shadowNotificationManager.getNotification(notificationId)).isNull()

        // Verify interaction count is incremented
        assertThat(appLockDataRepository.getDiscoveryNotificationInteractionCount()).isEqualTo(1)

        // Verify service stops itself
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(result).isEqualTo(START_NOT_STICKY)
    }

    @Test
    fun onStart_maxNotificationInteractionExceededAndDismiss_permanentlyDismissNotification() =
        runTest {
            val notificationId = 123
            val intent =
                Intent(context, NotificationInteractionService::class.java).apply {
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                }

            // Simulate first notification
            notificationManager.notify(notificationId, Notification.Builder(context).build())
            service.onStartCommand(intent, 0, 1)

            // Simulate second notification
            notificationManager.notify(notificationId, Notification.Builder(context).build())
            service.onStartCommand(intent, 0, 1)

            // Verify interaction count is incremented
            assertThat(appLockDataRepository.getDiscoveryNotificationInteractionCount())
                .isEqualTo(2)
            assertThat(appLockDataRepository.discoveryNotificationPermanentlyDismissed()).isFalse()

            // Simulate third notification
            notificationManager.notify(notificationId, Notification.Builder(context).build())
            intent.putExtra(EXTRA_NOTIFICATION_DISMISS, true)
            service.onStartCommand(intent, 0, 1)

            assertThat(appLockDataRepository.getDiscoveryNotificationInteractionCount())
                .isEqualTo(3)
            assertThat(appLockDataRepository.discoveryNotificationPermanentlyDismissed()).isTrue()
        }

    @Test
    fun onStart_withoutNotificationId_doesNotCancelOrIncrementAndStopsSelf() = runTest {
        val intent = Intent(context, NotificationInteractionService::class.java)

        val result = service.onStartCommand(intent, 0, 1)

        // Verify no notification is cancelled
        assertThat(shadowNotificationManager.size()).isEqualTo(0)

        // Verify interaction count is NOT incremented
        assertThat(appLockDataRepository.getDiscoveryNotificationInteractionCount()).isEqualTo(0)

        // Verify service stops itself
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(result).isEqualTo(START_NOT_STICKY)
    }
}
