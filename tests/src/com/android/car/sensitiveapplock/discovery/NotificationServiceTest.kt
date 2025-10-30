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

import android.app.Application
import android.app.NotificationManager
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.power.CarPowerManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import android.provider.Settings.ACTION_SETTINGS
import androidx.core.app.NotificationCompat.EXTRA_NOTIFICATION_ID
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.notification.NotificationInteractionService
import com.android.car.sensitiveapplock.notification.NotificationService
import com.android.car.sensitiveapplock.notification.NotificationService.Companion.EXTRA_NOTIFICATION_DISMISS
import com.android.car.sensitiveapplock.service.CarPowerMonitor
import com.android.car.sensitiveapplock.service.PackageChangeMonitor
import com.android.car.sensitiveapplock.settings.SettingsActivity
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotificationServiceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext
    @Inject lateinit var packageChangeMonitor: PackageChangeMonitor
    @Inject lateinit var carPowerMonitor: CarPowerMonitor
    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var car: Car

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val shadowNotificationManager = shadowOf(notificationManager)
    private val powerStateListenerCaptor = argumentCaptor<CarPowerManager.CarPowerStateListener>()
    private val uxStateListenerCaptor =
        argumentCaptor<CarUxRestrictionsManager.OnUxRestrictionsChangedListener>()

    private lateinit var notificationService: NotificationService
    private lateinit var spyCarPowerMonitor: CarPowerMonitor
    private lateinit var spyPackageChangeMonitor: PackageChangeMonitor
    private lateinit var carPowerManager: CarPowerManager
    private lateinit var carUxRestrictionsManager: CarUxRestrictionsManager

    @Before
    fun init() {
        hiltRule.inject()

        spyPackageChangeMonitor = spy(packageChangeMonitor)
        spyCarPowerMonitor = spy(carPowerMonitor)
        carPowerManager = car.getCarManager(CarPowerManager::class.java)!!
        carUxRestrictionsManager = car.getCarManager(CarUxRestrictionsManager::class.java)!!

        whenever(carPowerManager.powerState) doReturn CarPowerManager.STATE_ON
        // This is needed because the notification icon is resolved from the installed settings app
        AppInstallationHelper.addAppToPackageManager(context, SETTINGS_APP, SETTINGS_INTENT_FILTER)

        notificationService =
            NotificationService(
                context,
                spyPackageChangeMonitor,
                spyCarPowerMonitor,
                appLockDataRepository,
                pinManager,
                car,
                backgroundContext,
            )
    }

    @After
    fun cleanUp() {
        notificationService.stop()
    }

    @Test
    fun init_createsNotificationChannel() {
        val channel = notificationManager.getNotificationChannel(IMPORTANCE_HIGH)
        assertThat(channel).isNotNull()
        assertThat(channel.name).isEqualTo(NOTIFICATION_CHANNEL_NAME)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun onStart_registersListeners() {
        notificationService.start()

        verify(spyPackageChangeMonitor).addListener(any())
        verify(spyCarPowerMonitor).addListener(any())
        verify(carUxRestrictionsManager).registerListener(any())
    }

    @Test
    fun onStart_pinSet_doesNotRegisterListeners() = runTest {
        pinManager.setAppLockPin("1234")

        notificationService.start()

        verify(spyPackageChangeMonitor, never()).addListener(any())
        verify(spyCarPowerMonitor, never()).addListener(any())
        verify(carUxRestrictionsManager, never()).registerListener(any())
    }

    @Test
    fun onStart_notificationPermanentlyDismissed_doesNotRegisterListeners() = runTest {
        appLockDataRepository.permanentlyDismissDiscoveryNotification()

        notificationService.start()

        verify(spyPackageChangeMonitor, never()).addListener(any())
        verify(spyCarPowerMonitor, never()).addListener(any())
        verify(carUxRestrictionsManager, never()).registerListener(any())
    }

    @Test
    fun onStop_unregistersListeners() = runTest {
        notificationService.start()
        notificationService.stop()

        verify(spyPackageChangeMonitor).removeListener(any())
        verify(spyCarPowerMonitor).removeListener(any())
        verify(carUxRestrictionsManager).unregisterListener()
    }

    @Test
    fun onPackageAdded_notDriving_notifiesUser() = runTest {
        notificationService.start()

        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertDiscoveryNotification()
    }

    @Test
    fun onPackageAdded_isDriving_doesNotNotifyUser() = runTest {
        notificationService.start()

        shiftGear(drive = true)
        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNull()
    }

    @Test
    fun onPackageAdded_userShiftsToPark_notifiesUser() = runTest {
        notificationService.start()

        shiftGear(drive = true)
        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        // Simulate shifting to park
        shiftGear(drive = false)
        verify(carUxRestrictionsManager).registerListener(uxStateListenerCaptor.capture())
        uxStateListenerCaptor.firstValue.onUxRestrictionsChanged(
            CarUxRestrictions.Builder(
                    false, // isRequiresDistractionOptimization
                    0,
                    0,
                )
                .build()
        )

        assertDiscoveryNotification()
    }

    @Test
    fun onPackageAdded_carNotOn_doesNotNotifyUser() = runTest {
        notificationService.start()

        toggleCarPowerState(on = false)
        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNull()
    }

    @Test
    fun onPackageAdded_whenCarPoweredOff_userPowersOn_notifiesUser() = runTest {
        notificationService.start()

        // Simulate car powering off
        toggleCarPowerState(on = false)
        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        // Simulate car powering on
        toggleCarPowerState(on = true)
        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
        powerStateListenerCaptor.firstValue.onStateChanged(CarPowerManager.STATE_ON)

        assertDiscoveryNotification()
    }

    @Test
    fun onPackageAdded_pinSet_doesNotNotify() = runTest {
        notificationService.start()
        pinManager.setAppLockPin("1234")

        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNull()
    }

    @Test
    fun onPackageAdded_notificationPermanentlyDismissed_doesNotNotify() = runTest {
        notificationService.start()
        appLockDataRepository.permanentlyDismissDiscoveryNotification()

        // Simulate package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNull()
    }

    @Test
    fun onPackageAdded_alreadyNotifiedInCurrentSession_doesNotNotifyAgain() = runTest {
        notificationService.start()

        // Simulate first package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        // First notification sent
        assertThat(shadowNotificationManager.activeNotifications).hasLength(1)

        // Simulate second package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        // No new notifications have been sent
        assertThat(shadowNotificationManager.activeNotifications).hasLength(1)
    }

    @Test
    fun onPackageAdded_inNewDriveSession_notifiesUser() = runTest {
        notificationService.start()

        // Simulate first package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        // First notification sent
        assertThat(shadowNotificationManager.activeNotifications).hasLength(1)
        notificationManager.cancelAll()

        // Simulate shutdown
        verify(carPowerManager).setListener(any(), powerStateListenerCaptor.capture())
        powerStateListenerCaptor.firstValue.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE)

        // Simulate second package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        assertThat(shadowNotificationManager.activeNotifications).hasLength(1)
    }

    @Test
    fun onPackageAdded_whenPackageUpdated_doesNotNotifyUser() = runTest {
        notificationService.start()

        val intent = Intent(BROADCAST_INTENT).putExtra(Intent.EXTRA_REPLACING, true)
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNull()
    }

    @Test
    fun dismissDiscoveryNotification_startsNotificationInteractionService() = runTest {
        NotificationService.dismissDiscoveryNotification(context)

        val shadowApplication = shadowOf(context as Application)
        val startedServices = shadowApplication.allStartedServices
        val notificationServices =
            startedServices.filter { intent ->
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1) == DISCOVERY_NOTIFICATION_ID &&
                    intent.component?.className ==
                        NotificationInteractionService::class.qualifiedName
            }
        assertThat(notificationServices).hasSize(1)
    }

    @Test
    fun notification_notAtMaxInteractionCount_showsInitialText() = runTest {
        notificationService.start()

        // Simulate first package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        val shadowNotification = shadowOf(notification)
        val actions = notification.actions

        assertThat(shadowNotification.contentTitle)
            .isEqualTo(context.getString(R.string.discovery_notification_title))
        assertThat(shadowNotification.contentText)
            .isEqualTo(context.getString(R.string.discovery_notification_message))
        assertThat(actions[0].title) // Primary CTA
            .isEqualTo(context.getString(R.string.discovery_notification_setup_button_text))
        assertThat(actions[1].title) // Secondary CTA
            .isEqualTo(context.getString(R.string.discovery_notification_dismiss_button_text))
    }

    @Test
    fun notification_atMaxInteractionCount_showsMaxInteractionText() = runTest {
        notificationService.start()

        // Increment interaction count to maximum
        val maxCount =
            context.resources.getInteger(R.integer.config_discovery_notification_max_interaction)
        repeat(maxCount) { appLockDataRepository.incrementDiscoveryNotificationInteractionCount() }
        // Simulate first package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        val shadowNotification = shadowOf(notification)
        val actions = notification.actions

        assertThat(actions[1].title) // Secondary CTA
            .isEqualTo(
                context.getString(R.string.discovery_notification_permanent_dismiss_button_text)
            )
    }

    @Test
    fun notification_greaterThanMaxInteractionCount_showsMaxInteractionText() = runTest {
        notificationService.start()

        // Increment interaction count to greater than maximum
        val maxCount =
            context.resources.getInteger(R.integer.config_discovery_notification_max_interaction)
        repeat(maxCount + 1) {
            appLockDataRepository.incrementDiscoveryNotificationInteractionCount()
        }
        // Simulate first package install
        context.sendBroadcast(BROADCAST_INTENT)
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        val shadowNotification = shadowOf(notification)
        val actions = notification.actions

        assertThat(actions[1].title) // Secondary CTA
            .isEqualTo(
                context.getString(R.string.discovery_notification_permanent_dismiss_button_text)
            )
    }

    private fun assertDiscoveryNotification() {
        val notification = shadowNotificationManager.getNotification(DISCOVERY_NOTIFICATION_ID)
        assertThat(notification).isNotNull()
        assertThat(shadowNotificationManager.activeNotifications).hasLength(1)
        assertThat(notification.extras.getBoolean(NOTIFICATION_EXTRA_USE_LAUNCHER_ICON)).isFalse()

        val actions = notification.actions.asList()
        assertThat(actions).hasSize(2)

        // Assert Primary Action CTA
        var shadowIntent = shadowOf(actions[0].actionIntent)
        var savedIntent = shadowIntent.savedIntent
        assertThat(shadowIntent.isActivity).isTrue()
        assertThat(shadowIntent.requestCode).isEqualTo(DISCOVERY_NOTIFICATION_ID)
        assertThat(savedIntent.component!!.className)
            .isEqualTo(SettingsActivity::class.qualifiedName)
        assertThat(savedIntent.extras!!.getInt(EXTRA_NOTIFICATION_ID))
            .isEqualTo(DISCOVERY_NOTIFICATION_ID)

        // Assert Secondary Action CTA
        shadowIntent = shadowOf(actions[1].actionIntent)
        savedIntent = shadowIntent.savedIntent
        assertThat(shadowIntent.isService).isTrue()
        assertThat(shadowIntent.requestCode).isEqualTo(DISCOVERY_NOTIFICATION_ID)
        assertThat(savedIntent.component!!.className)
            .isEqualTo(NotificationInteractionService::class.qualifiedName)
        assertThat(savedIntent.extras!!.getInt(EXTRA_NOTIFICATION_ID))
            .isEqualTo(DISCOVERY_NOTIFICATION_ID)
        assertThat(savedIntent.extras!!.getBoolean(EXTRA_NOTIFICATION_DISMISS)).isTrue()

        // Assert Content Intent
        shadowIntent = shadowOf(notification.contentIntent)
        savedIntent = shadowIntent.savedIntent
        assertThat(shadowIntent.isActivity).isTrue()
        assertThat(shadowIntent.requestCode).isEqualTo(DISCOVERY_NOTIFICATION_ID)
        assertThat(savedIntent.component!!.className)
            .isEqualTo(SettingsActivity::class.qualifiedName)
        assertThat(savedIntent.extras!!.getInt(EXTRA_NOTIFICATION_ID))
            .isEqualTo(DISCOVERY_NOTIFICATION_ID)
    }

    private fun shiftGear(drive: Boolean) {
        whenever(carUxRestrictionsManager.currentCarUxRestrictions) doReturn
            CarUxRestrictions.Builder(
                    drive, // isRequiresDistractionOptimization
                    0,
                    0,
                )
                .build()
    }

    private fun toggleCarPowerState(on: Boolean) {
        if (on) {
            whenever(carPowerManager.powerState) doReturn CarPowerManager.STATE_ON
        } else {
            whenever(carPowerManager.powerState) doReturn CarPowerManager.STATE_POST_SHUTDOWN_ENTER
        }
    }

    private companion object {
        const val IMPORTANCE_HIGH = "importance_high"
        const val NOTIFICATION_CHANNEL_NAME = "Importance High"
        const val TEST_PACKAGE_NAME = "com.package.ok"
        const val NOTIFICATION_EXTRA_USE_LAUNCHER_ICON =
            "com.android.car.notification.EXTRA_USE_LAUNCHER_ICON"
        const val DISCOVERY_NOTIFICATION_ID = 1001
        const val SETTINGS_APP = "com.android.settings.app"

        val SETTINGS_INTENT_FILTER =
            IntentFilter(ACTION_SETTINGS).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        val BROADCAST_INTENT =
            Intent(Intent.ACTION_PACKAGE_ADDED).setData(Uri.parse("package:$TEST_PACKAGE_NAME"))
    }
}
