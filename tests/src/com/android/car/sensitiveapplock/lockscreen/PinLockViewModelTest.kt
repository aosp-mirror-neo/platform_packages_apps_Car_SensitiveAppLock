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
package com.android.car.sensitiveapplock.lockscreen

import android.Manifest.permission.SUSPEND_APPS
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Application
import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.car.media.CarMediaIntents
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.os.Process
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.RecoveryAccount
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListDataSource
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.settings.SettingsLockManager
import com.android.car.sensitiveapplock.shadows.ShadowAccountManager
import com.android.car.sensitiveapplock.shadows.ShadowResources
import com.android.car.sensitiveapplock.testing.AppInstallationHelper
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.DEFAULT_FLAGS
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.addMediaAppToPackageManager
import com.android.car.sensitiveapplock.testing.AppInstallationHelper.buildLauncherActivityInfo
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowResources::class, ShadowAccountManager::class])
class PinLockViewModelTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val shadowPackageManager = shadowOf(context.packageManager)
    private val shadowLauncherApps =
        shadowOf(context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
    private val shadowStorageStatsManager =
        shadowOf(context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager)

    private lateinit var pinLockViewModel: PinLockViewModel

    @Inject lateinit var appLockDataRepository: AppLockDataRepository
    @Inject lateinit var appSuspensionManager: AppSuspensionManager

    @Inject lateinit var settingsLockManager: SettingsLockManager

    @Inject lateinit var pinManager: PinManager

    @Inject lateinit var lockableAppsListDataSource: LockableAppsListDataSource
    @Inject @BackgroundContext lateinit var backgroundContext: CoroutineContext

    @Before
    fun init() {
        hiltRule.inject()
        shadowOf(context).grantPermissions(SUSPEND_APPS)
        pinLockViewModel =
            PinLockViewModel(
                context,
                appLockDataRepository,
                appSuspensionManager,
                pinManager,
                settingsLockManager,
                lockableAppsListDataSource,
                backgroundContext,
            )
        ShadowResources.reset()
        shadowStorageStatsManager.clearStorageStats()
    }

    @Test
    fun setEnteredPin_setsEnteredPin() {
        pinLockViewModel.setEnteredPin(USER_PIN)

        assertThat(pinLockViewModel.enteredPin.value).isEqualTo(USER_PIN)
    }

    @Test
    fun isSavedPin_ifCorrectPin_returnsTrue() = runTest {
        pinManager.setAppLockPin(USER_PIN)

        assertThat(pinLockViewModel.isSavedPin(USER_PIN)).isTrue()
    }

    @Test
    fun isSavedPin_ifIncorrectPin_returnsFalse() = runTest {
        assertThat(pinLockViewModel.isSavedPin(USER_PIN)).isFalse()
    }

    @Test
    fun savePin_savesPinToDb() = runTest {
        pinLockViewModel.savePin(USER_PIN)

        assertThat(pinManager.verifyAppLockPin(USER_PIN)).isTrue()
    }

    @Test
    fun unlockApp_unsuspendsSpecificApp() = runTest {
        for (packageName in TEST_PACKAGE_NAMES) {
            shadowPackageManager.installPackage(
                PackageInfo().apply { this.packageName = packageName }
            )
            appSuspensionManager.setAppSuspensionState(packageName, true)
            appLockDataRepository.addLockedApp(packageName)
        }

        pinLockViewModel.unlockApp(TEST_PACKAGE_NAMES[0])

        // Only first app should be unlocked
        assertThat(shadowPackageManager.getPackageSetting(TEST_PACKAGE_NAMES[0]).isSuspended)
            .isFalse()
        TEST_PACKAGE_NAMES.drop(1)
            .all { shadowPackageManager.getPackageSetting(it).isSuspended }
            .let { allSuspended -> assertThat(allSuspended).isTrue() }
    }

    @Test
    fun getLaunchIntentForPackage_standardApp_returnsIntentWithActionMain() {
        val packageName = TEST_PACKAGE_NAMES[0]
        val intentFilter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        AppInstallationHelper.addAppToPackageManager(context, packageName, intentFilter)

        val intent = pinLockViewModel.getLaunchIntentForPackage(context.packageManager, packageName)

        assertThat(intent?.action).isEqualTo(Intent.ACTION_MAIN)
    }

    @Test
    fun getLaunchIntentForPackage_templateMediaApp_returnsIntentWithActionMediaTemplate() {
        addMediaAppToPackageManager(context, TEST_TEMPLATE_MEDIA_PACKAGE)

        val intent =
            pinLockViewModel.getLaunchIntentForPackage(
                context.packageManager,
                TEST_TEMPLATE_MEDIA_PACKAGE,
            )

        assertThat(intent?.action).isEqualTo(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
    }

    @Test
    fun enableReAuthRecoveryFlow_whenNoAccountSignedIn_returnsFalse() = runTest {
        assertThat(pinLockViewModel.enableReAuthRecoveryFlow()).isFalse()
        assertThat(appLockDataRepository.getReAuthPinRecoveryAccount())
            .isEqualTo(RecoveryAccount.getDefaultInstance())
    }

    @Test
    fun enableReAuthRecoveryFlow_whenAccountSignedIn_returnsTrue() = runTest {
        ShadowResources.setString(R.string.config_recoveryAccountType, RECOVERY_ACCOUNT_TYPE)
        val shadowAccountManager = shadowOf(AccountManager.get(context))
        shadowAccountManager.addAccount(Account("TEST_ACCOUNT", RECOVERY_ACCOUNT_TYPE))

        assertThat(pinLockViewModel.enableReAuthRecoveryFlow()).isTrue()
    }

    @Test
    fun enableReAuthRecoveryFlow_whenAccountIsNotRecoveryType_returnsFalse() = runTest {
        ShadowResources.setString(R.string.config_recoveryAccountType, RECOVERY_ACCOUNT_TYPE)
        val shadowAccountManager = shadowOf(AccountManager.get(context))
        shadowAccountManager.addAccount(Account("TEST_ACCOUNT", "com.test"))

        assertThat(pinLockViewModel.enableReAuthRecoveryFlow()).isFalse()
    }

    @Test
    fun getReAuthIntent_whenRecoveryEnabled_returnsRecoveryAccount() = runTest {
        ShadowResources.setString(R.string.config_recoveryAccountType, RECOVERY_ACCOUNT_TYPE)
        val shadowAccountManager = shadowOf(AccountManager.get(context))
        shadowAccountManager.addAccount(Account("TEST_ACCOUNT", RECOVERY_ACCOUNT_TYPE))
        shadowAccountManager.addAuthenticator(RECOVERY_ACCOUNT_TYPE)

        pinLockViewModel.enableReAuthRecoveryFlow()

        assertThat(pinLockViewModel.getReAuthIntent()).isNotNull()
    }

    @Test
    fun getReAuthIntent_whenRecoveryNotEnabled_returnsNull() = runTest {
        assertThat(pinLockViewModel.getReAuthIntent()).isNull()
    }

    @Test
    fun getAddAccountIntent_returnsIntent() = runTest {
        val accountType = context.resources.getString(R.string.config_recoveryAccountType)
        shadowOf(AccountManager.get(context)).addAuthenticator(accountType)

        assertThat(pinLockViewModel.getAddAccountIntent()).isNotNull()
    }

    @Test
    fun getAddAccountIntent_whenNoAuthenticatorAdded_returnsNull() = runTest {
        assertThat(pinLockViewModel.getAddAccountIntent()).isNull()
    }

    @Test
    fun getLockedApps_returnsOnlyLockedAppInfo() = runTest {
        var launcherActivityInfo =
            AppInstallationHelper.buildLauncherActivityInfo(TEST_PACKAGE_NAMES[0])
        shadowLauncherApps.addActivity(Process.myUserHandle(), launcherActivityInfo)
        launcherActivityInfo =
            AppInstallationHelper.buildLauncherActivityInfo(TEST_PACKAGE_NAMES[1])
        shadowLauncherApps.addActivity(Process.myUserHandle(), launcherActivityInfo)
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])

        val lockedApps = pinLockViewModel.getLockedApps()

        assertThat(lockedApps).hasSize(1)
        assertThat(lockedApps[0].packageName).isEqualTo(TEST_PACKAGE_NAMES[0])
    }

    @Test
    fun getLockedDataClearedSystemApps_returnsOnlyLockedAppsWithDataCleared() = runTest {
        var launcherActivityInfo =
            buildLauncherActivityInfo(
                TEST_PACKAGE_NAMES[0],
                uid = 1,
                ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
            )
        shadowLauncherApps.addActivity(Process.myUserHandle(), launcherActivityInfo)
        launcherActivityInfo =
            buildLauncherActivityInfo(
                TEST_PACKAGE_NAMES[1],
                uid = 2,
                ApplicationInfo.FLAG_SYSTEM or DEFAULT_FLAGS,
            )
        shadowLauncherApps.addActivity(Process.myUserHandle(), launcherActivityInfo)
        launcherActivityInfo = buildLauncherActivityInfo(TEST_PACKAGE_NAMES[2], uid = 3)
        shadowLauncherApps.addActivity(Process.myUserHandle(), launcherActivityInfo)
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[0])
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[1])
        appLockDataRepository.addLockedApp(TEST_PACKAGE_NAMES[2])
        shadowStorageStatsManager.addStorageStats(
            StorageManager.UUID_DEFAULT,
            TEST_PACKAGE_NAMES[0],
            Process.myUserHandle(),
            createStorageStats(DEFAULT_EMPTY_APP_SIZE + 10), // App with some user data
        )
        shadowStorageStatsManager.addStorageStats(
            StorageManager.UUID_DEFAULT,
            TEST_PACKAGE_NAMES[1],
            Process.myUserHandle(),
            createStorageStats(DEFAULT_EMPTY_APP_SIZE),
        )
        shadowStorageStatsManager.addStorageStats(
            StorageManager.UUID_DEFAULT,
            TEST_PACKAGE_NAMES[2],
            Process.myUserHandle(),
            createStorageStats(DEFAULT_EMPTY_APP_SIZE),
        )

        val apps = pinLockViewModel.getLockedDataClearedSystemApps()

        assertThat(apps).containsExactly(TEST_PACKAGE_NAMES[1])
    }

    private companion object {
        const val USER_PIN = "1111"
        const val TEST_TEMPLATE_MEDIA_PACKAGE = "com.template.1"
        const val RECOVERY_ACCOUNT_TYPE = "com.oem"
        const val DEFAULT_EMPTY_APP_SIZE = 24576L // Empty directory metadata size

        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")

        fun createStorageStats(dataBytes: Long): StorageStats {
            return mock<StorageStats> { on { getDataBytes() } doReturn dataBytes }
        }
    }
}
