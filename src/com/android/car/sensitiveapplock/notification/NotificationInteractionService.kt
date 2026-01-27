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

package com.android.car.sensitiveapplock.notification

import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.notification.NotificationService.Companion.EXTRA_NOTIFICATION_DISMISS
import com.android.car.sensitiveapplock.notification.NotificationService.Companion.NOTIFICATION_POSTED_KEY
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * A [LifecycleService] responsible for handling interactions with notifications, specifically for
 * the Sensitive App Lock discovery notification.
 *
 * This service is triggered when a user interacts with the discovery notification. It cancels the
 * notification and updates the interaction count in [AppLockDataRepository]. If the interaction
 * count exceeds a predefined threshold and the user has explicitly canceled the notification, the
 * discovery notification is permanently dismissed.
 *
 * The service stops itself after processing the notification interaction.
 */
@AndroidEntryPoint(LifecycleService::class)
class NotificationInteractionService : Hilt_NotificationInteractionService() {
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var metricsLogger: MetricsLogger
    @Inject lateinit var sharedPreferences: DataStore<Preferences>

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val featureEnableDiscoveryNotification =
            resources.getBoolean(R.bool.feature_enableDiscoveryNotification)
        if (!featureEnableDiscoveryNotification) {
            logger.v("Service started but feature is disabled")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notificationId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (notificationId == null || notificationId == -1) {
            logger.d("Service started without notification id in intent.extra")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        lifecycleScope.launch {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            sharedPreferences.edit { it[NOTIFICATION_POSTED_KEY] = false }
            logger.v("Discovery notification dismissed")
            appLockDataRepository.incrementDiscoveryNotificationInteractionCount()
            maybePermanentlyDismissNotification(intent)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun maybePermanentlyDismissNotification(intent: Intent?) {
        val interactionCount = appLockDataRepository.getDiscoveryNotificationInteractionCount()
        val maxNotificationInteraction =
            resources.getInteger(R.integer.config_discovery_notification_max_interaction)
        val explicitDismiss = intent?.getBooleanExtra(EXTRA_NOTIFICATION_DISMISS, false)
        if (explicitDismiss != true) {
            return
        }
        if (interactionCount > maxNotificationInteraction) {
            logger.v("Permanently dismissing discovery notification")
            appLockDataRepository.permanentlyDismissDiscoveryNotification()
            metricsLogger.logAppLockEvent(AppLockEvent.DISCOVERY_NOTIFICATION_PERMANENTLY_DISMISSED)
        } else {
            metricsLogger.logAppLockEvent(AppLockEvent.DISCOVERY_NOTIFICATION_DISMISSED)
        }
    }

    private companion object {
        val logger = Logger(NotificationInteractionService::class.java)
    }
}
