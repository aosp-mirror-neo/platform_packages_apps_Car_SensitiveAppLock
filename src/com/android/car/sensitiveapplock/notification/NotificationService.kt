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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.power.CarPowerManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings.ACTION_SETTINGS
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.metrics.AppLockEvent
import com.android.car.sensitiveapplock.metrics.MetricsLogger
import com.android.car.sensitiveapplock.service.AppLockService
import com.android.car.sensitiveapplock.service.CarPowerMonitor
import com.android.car.sensitiveapplock.service.PackageChangeMonitor
import com.android.car.sensitiveapplock.settings.SettingsActivity
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * An [AppLockService] for sending App Lock discovery notifications.
 *
 * This service sends discovery notifications when all the following conditions are satisfied:
 * - The user has installed an app.
 * - The user has not set up app lock.
 * - The user is not driving, otherwise notification is shown after the user shifts into park.
 * - The car is not powered off, otherwise notification is shown when the car powers on.
 * - Discovery notification was not shown in the current drive session.
 * - The user has not permanently dismissed the notification.
 */
@Singleton
class NotificationService
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val packageChangeMonitor: PackageChangeMonitor,
    private val carPowerMonitor: CarPowerMonitor,
    private val appLockDataRepository: AppLockDataRepository,
    private val pinManager: PinManager,
    private val metricsLogger: MetricsLogger,
    private val sharedPreferences: DataStore<Preferences>,
    car: Car?,
    @BackgroundContext backgroundContext: CoroutineContext,
) : AppLockService {
    private var notificationShowInCurrentSession = false
    private var pendingNotification = false

    private val scope = CoroutineScope(backgroundContext)
    private val carUxRestrictionsManager = car?.getCarManager(CarUxRestrictionsManager::class.java)
    private val carPowerManager = car?.getCarManager(CarPowerManager::class.java)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val uxRestrictionsListener =
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener {
            logger.v("car ux restrictions changed")
            if (!isDriving() && pendingNotification) {
                scope.launch { notifyUser() }
            }
        }
    private val carPowerMonitorListener =
        object : CarPowerMonitor.Listener {
            override fun onPowerStateChange(powerState: Int) {
                if (powerState == CarPowerManager.STATE_SHUTDOWN_PREPARE) {
                    // Reset for new session
                    notificationShowInCurrentSession = false
                }
                if (isCarOn() && pendingNotification) {
                    scope.launch { notifyUser() }
                }
            }
        }
    private val packageChangeListener =
        object : PackageChangeMonitor.Listener {
            override fun onPackageAdded(packageName: String, replacing: Boolean) {
                // User should only be notified on new app installs
                if (replacing) {
                    return
                }
                if (isDriving()) {
                    pendingNotification = true
                    return
                }
                if (!isCarOn()) {
                    pendingNotification = true
                    return
                }
                scope.launch { notifyUser() }
            }
        }

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                IMPORTANCE_HIGH, // id
                "Importance High", // name
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                IMPORTANCE_LOW, // id
                "Importance Low", // name
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    override fun start() {
        scope.launch {
            if (!shouldInitService()) {
                return@launch
            }
            logger.v("staring notification service")
            carUxRestrictionsManager?.registerListener(uxRestrictionsListener)
            carPowerMonitor.addListener(carPowerMonitorListener)
            packageChangeMonitor.addListener(packageChangeListener)

            // Notifications are dismissed on reboots. If a notification was posted before reboot,
            // it must be posted again
            notifyOnStart()
        }
    }

    override fun stop() {
        carUxRestrictionsManager?.unregisterListener()
        carPowerMonitor.removeListener(carPowerMonitorListener)
        packageChangeMonitor.removeListener(packageChangeListener)
        scope.cancel()
    }

    private suspend fun notifyOnStart() {
        val posted = sharedPreferences.data.map { it[NOTIFICATION_POSTED_KEY] ?: false }.first()
        if (posted) {
            val notification = createNotification(IMPORTANCE_LOW)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun notifyUser() {
        if (!shouldNotifyUser()) {
            logger.v("not notifying user")
            return
        }

        logger.v("notifying user")
        val notification = createNotification(IMPORTANCE_HIGH)
        notificationManager.notify(NOTIFICATION_ID, notification)
        sharedPreferences.edit { it[NOTIFICATION_POSTED_KEY] = true }
        notificationShowInCurrentSession = true
        pendingNotification = false
        metricsLogger.logAppLockEvent(AppLockEvent.DISCOVERY_NOTIFICATION_SHOWN)
    }

    private suspend fun createNotification(channelId: String): Notification {
        val positiveActionText =
            context.getString(R.string.discovery_notification_setup_button_text)
        val positiveAction =
            Notification.Action.Builder(
                    0, // icon
                    positiveActionText, // title
                    createSettingsActivityLaunchIntent(), // intent
                )
                .build()
        val dismissAction =
            Notification.Action.Builder(
                    0, // icon
                    getNotificationDismissActionText(), // title
                    createDismissIntent(), // intent
                )
                .build()

        val largeIconBitmap = getNotificationIcon()
        val titleText = context.getString(R.string.discovery_notification_title)
        val bodyText = context.getString(R.string.discovery_notification_message)

        val notification =
            Notification.Builder(context, channelId)
                .setContentTitle(titleText)
                .setContentText(bodyText)
                .addAction(positiveAction)
                .addAction(dismissAction)
                .setContentIntent(createSettingsActivityLaunchIntent())
                .setLargeIcon(largeIconBitmap)
                .setSmallIcon(Icon.createWithAdaptiveBitmap(largeIconBitmap))
                .addExtras(
                    Bundle().apply { putBoolean(NOTIFICATION_EXTRA_USE_LAUNCHER_ICON, false) }
                )
                .build()
        return notification
    }

    private suspend fun getNotificationDismissActionText(): String {
        val maxCount =
            context.resources.getInteger(R.integer.config_discovery_notification_max_interaction)
        val currentCount = appLockDataRepository.getDiscoveryNotificationInteractionCount()

        return if (currentCount >= maxCount) {
            context.getString(R.string.discovery_notification_permanent_dismiss_button_text)
        } else {
            context.getString(R.string.discovery_notification_dismiss_button_text)
        }
    }

    private fun createSettingsActivityLaunchIntent(): PendingIntent {
        val actionIntent =
            Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createDismissIntent(): PendingIntent {
        val actionIntent =
            Intent(context, NotificationInteractionService::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                putExtra(EXTRA_NOTIFICATION_DISMISS, true)
            }
        return PendingIntent.getService(
            context,
            NOTIFICATION_ID,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun getNotificationIcon(): Bitmap? {
        // We will use the default Settings Icon
        return context.packageManager
            .queryIntentActivities(
                Intent(ACTION_SETTINGS).addCategory(Intent.CATEGORY_DEFAULT),
                0, // flags
            )
            .firstOrNull()
            ?.loadIcon(context.packageManager)
            ?.toBitmap()
    }

    private suspend fun shouldInitService(): Boolean {
        if (pinManager.getAppLockPinState() == PinManager.PinState.SET) {
            logger.v("Not initializing service; Pin is set")
            return false
        }
        if (appLockDataRepository.discoveryNotificationPermanentlyDismissed()) {
            logger.v("Not initializing service; Permanently dismissed")
            return false
        }
        return true
    }

    private suspend fun shouldNotifyUser(): Boolean {
        if (pinManager.getAppLockPinState() == PinManager.PinState.SET) {
            logger.v("Not showing notification; Pin is set")
            return false
        }
        if (appLockDataRepository.discoveryNotificationPermanentlyDismissed()) {
            logger.v("Not showing notification; Permanently Dismissed")
            return false
        }
        if (isDriving()) {
            logger.v("Not showing notification; User is not parked")
            return false
        }
        if (!isCarOn()) {
            logger.v("Not showing notification; Car is not powered on")
            return false
        }
        if (notificationShowInCurrentSession) {
            logger.v("Already notified user in current session")
            return false
        }
        return true
    }

    private fun isDriving(): Boolean {
        return carUxRestrictionsManager
            ?.currentCarUxRestrictions
            ?.isRequiresDistractionOptimization == true
    }

    private fun isCarOn(): Boolean {
        // STATE_SUSPEND_EXIT/STATE_HIBERNATION_EXIT/STATE_WAIT_FOR_VHAL all lead to STATE_ON. See
        // https://source.android.com/docs/devices/automotive/power/power#state
        return carPowerManager?.powerState == CarPowerManager.STATE_ON
    }

    companion object {
        private val logger = Logger(NotificationService::class.java)
        private const val IMPORTANCE_HIGH = "importance_high"
        private const val IMPORTANCE_LOW = "importance_low"
        private const val NOTIFICATION_EXTRA_USE_LAUNCHER_ICON =
            "com.android.car.notification.EXTRA_USE_LAUNCHER_ICON"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_NOTIFICATION_DISMISS =
            "com.android.car.notification.EXTRA_NOTIFICATION_DISMISS"

        // SharedPreference key to track if a notification was posted.
        val NOTIFICATION_POSTED_KEY = booleanPreferencesKey("notification_shown_key")

        fun dismissDiscoveryNotification(context: Context) {
            val intent =
                Intent(context, NotificationInteractionService::class.java).apply {
                    putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                }
            context.startService(intent)
        }
    }
}
