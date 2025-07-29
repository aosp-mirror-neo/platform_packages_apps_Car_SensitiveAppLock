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
import android.car.media.CarMediaIntents
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.service.media.MediaBrowserService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListDataSource
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.settings.SettingsLockManager
import com.android.car.sensitiveapplock.shadows.ShadowAccountManager
import com.android.car.sensitiveapplock.shadows.ShadowResources
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
    fun unlockApps_unsuspendsAllApps() = runTest {
        for (packageName in TEST_PACKAGE_NAMES) {
            shadowPackageManager.installPackage(
                PackageInfo().apply { this.packageName = packageName }
            )
            appSuspensionManager.setAppSuspensionState(packageName, true)
            appLockDataRepository.addLockedApp(packageName)
        }

        pinLockViewModel.unlockApps()

        for (packageName in TEST_PACKAGE_NAMES) {
            assertThat(shadowPackageManager.getPackageSetting(packageName).isSuspended).isFalse()
        }
    }

    @Test
    fun getLaunchIntentForPackage_standardApp_returnsIntentWithActionMain() {
        val packageName = TEST_PACKAGE_NAMES[0]
        val cmpName = ComponentName(packageName, "TestActivity")
        val intentFilter =
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        shadowPackageManager.addActivityIfNotPresent(cmpName)
        shadowPackageManager.addIntentFilterForActivity(cmpName, intentFilter)

        val intent = pinLockViewModel.getLaunchIntentForPackage(context.packageManager, packageName)

        assertThat(intent?.action).isEqualTo(Intent.ACTION_MAIN)
    }

    @Test
    fun getLaunchIntentForPackage_templateMediaApp_returnsIntentWithActionMediaTemplate() {
        val cmpName = ComponentName(TEST_TEMPLATE_MEDIA_PACKAGE, "MediaBrowserService")
        val intentFilter = IntentFilter(MediaBrowserService.SERVICE_INTERFACE)
        shadowPackageManager.addServiceIfNotPresent(cmpName)
        shadowPackageManager.addIntentFilterForService(cmpName, intentFilter)

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

    private companion object {
        const val USER_PIN = "1111"
        const val TEST_TEMPLATE_MEDIA_PACKAGE = "com.template.1"
        const val RECOVERY_ACCOUNT_TYPE = "com.oem"

        val TEST_PACKAGE_NAMES = listOf("com.package.1", "com.package.2", "com.package.3")
    }
}
