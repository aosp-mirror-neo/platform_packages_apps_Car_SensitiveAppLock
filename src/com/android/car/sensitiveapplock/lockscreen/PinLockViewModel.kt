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

import android.accounts.AccountManager
import android.accounts.AccountManager.KEY_INTENT
import android.accounts.AccountsException
import android.app.usage.StorageStatsManager
import android.car.media.CarMediaIntents
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import androidx.lifecycle.ViewModel
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.auth.PinManager
import com.android.car.sensitiveapplock.data.AppInfo
import com.android.car.sensitiveapplock.data.AppLockDataRepository
import com.android.car.sensitiveapplock.data.LockableAppsListDataSource
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import com.android.car.sensitiveapplock.settings.SettingsLockManager
import com.android.car.sensitiveapplock.settings.SettingsLockStatus
import com.android.car.sensitiveapplock.util.AppSuspensionManager
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Viewmodel for the Pin Lock screen. */
@HiltViewModel
class PinLockViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val appLockDataRepository: AppLockDataRepository,
    private val appSuspensionManager: AppSuspensionManager,
    private val pinManager: PinManager,
    private val settingsLockManager: SettingsLockManager,
    private val lockableAppsListDataSource: LockableAppsListDataSource,
    @BackgroundContext private val backgroundContext: CoroutineContext,
) : ViewModel() {
    private val accountManager = AccountManager.get(context)
    private val storageStatsManager =
        context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    private val resources = context.resources
    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()

    /** Temporarily sets the user's pin in memory. */
    fun setEnteredPin(pin: String) {
        _enteredPin.update { currentState -> pin }
    }

    /** Checks if the entered PIN has a valid format. */
    fun doesPinHaveValidFormat(pin: String): Boolean = pinManager.doesPinHaveValidFormat(pin)

    /** Checks if the entered PIN matches the one stored in the database. */
    suspend fun isSavedPin(enteredPin: String): Boolean {
        return pinManager.verifyAppLockPin(enteredPin)
    }

    /**
     * Saves a user's PIN to the datastore.
     *
     * Returns false if the PIN failed to save.
     */
    suspend fun savePin(pin: String): Boolean = pinManager.setAppLockPin(pin)

    /** Unlocks the locked app. */
    fun unlockApp(packageName: String) {
        appSuspensionManager.setAppSuspensionState(packageName, state = false)
    }

    /**
     * Unlocks the Settings page by setting the Settings Lock Status to
     * [SettingsLockStatus.VALID_PIN].
     */
    fun unlockSettings() = settingsLockManager.setLockStatus(SettingsLockStatus.VALID_PIN)

    /**
     * Sets the Settings Lock Status to [SettingsLockStatus.CANCELED_PIN] if a valid status was not
     * already set.
     */
    suspend fun setCanceledIfNotValid() {
        if (settingsLockManager.lockStatusFlow.first() != SettingsLockStatus.VALID_PIN) {
            settingsLockManager.setLockStatus(SettingsLockStatus.CANCELED_PIN)
        }
    }

    /**
     * Returns an [Intent] to launch the action [Intent.ACTION_MAIN] for standard launcher app or
     * action [CarMediaIntents.ACTION_MEDIA_TEMPLATE] for templated media apps.
     */
    fun getLaunchIntentForPackage(packageManager: PackageManager, packageName: String): Intent? {
        val templateMediaApps =
            lockableAppsListDataSource.getLockableApps().filter { it.isTemplateMediaApp }
        val mediaApp = templateMediaApps.find { it.packageName == packageName }
        if (mediaApp == null) {
            return packageManager.getLaunchIntentForPackage(packageName)
        }
        val component = ComponentName(packageName, mediaApp.name)
        val intent =
            Intent(CarMediaIntents.ACTION_MEDIA_TEMPLATE)
                .putExtra(CarMediaIntents.EXTRA_MEDIA_COMPONENT, component.flattenToString())
        return intent
    }

    /**
     * Enables reAuth recovery flow if the user has already added a
     * [R.string.config_recoveryAccountType] type account to their profile. Returns true if the flow
     * is enabled, false otherwise.
     */
    suspend fun enableReAuthRecoveryFlow(): Boolean {
        val defaultAccountType = resources.getString(R.string.config_recoveryAccountType)
        val accounts = accountManager.getAccountsByType(defaultAccountType)
        if (accounts.isEmpty()) {
            appLockDataRepository.clearReAuthPinRecoveryAccount()
            return false
        }
        appLockDataRepository.setReAuthPinRecoveryAccount(accounts.first())
        return true
    }

    /**
     * Returns the [Intent] for adding an account of type [R.string.config_recoveryAccountType] if
     * an authenticator of the specified type is registered. Null otherwise.
     */
    suspend fun getAddAccountIntent(): Intent? =
        withContext(backgroundContext) {
            val defaultAccount = resources.getString(R.string.config_recoveryAccountType)
            val resultBundle =
                accountManager.addAccount(
                    defaultAccount,
                    null, // authTokenType
                    null, // requiredFeatures
                    null, // addAccountOptions
                    null, // activity
                    null, // callback
                    null, // handler
                )
            try {
                return@withContext resultBundle.result.getParcelable(KEY_INTENT, Intent::class.java)
            } catch (exception: AccountsException) {
                // We do not catch IO Exception as that is thrown when invoking the addAccount flow
                logger.w("Error occurred while fetching addAccountIntent", exception)
                return@withContext null
            }
        }

    /**
     * Returns the [Intent] for verifying the user's credentials associated with their recovery
     * account. Null if no recovery account was setup or the user is not logged into their recovery
     * account.
     */
    suspend fun getReAuthIntent(): Intent? =
        withContext(backgroundContext) {
            if (!appLockDataRepository.reAuthPinRecoveryEnabled()) {
                return@withContext null
            }
            val recoveryAccount = appLockDataRepository.getReAuthPinRecoveryAccount()
            val account =
                accountManager.accounts.find {
                    it.name == recoveryAccount.name && it.type == recoveryAccount.type
                }
            if (account == null) {
                return@withContext null
            }
            val resultBundle =
                accountManager.confirmCredentials(
                    account,
                    null, // options
                    null, // activity
                    null, // callback
                    null, // handle
                )
            try {
                return@withContext resultBundle.result.getParcelable(KEY_INTENT, Intent::class.java)
            } catch (exception: AccountsException) {
                // We do not catch the IO Exception as that is thrown when invoking the reAuth flow
                logger.w("Error occurred while fetching reAuthIntent", exception)
                return@withContext null
            }
        }

    /** Gets the [AppInfo] of all locked user apps. */
    suspend fun getLockedApps(): List<AppInfo> {
        val apps = appLockDataRepository.getLockedApps()
        val userLockableApps = lockableAppsListDataSource.getLockableApps()
        return userLockableApps.filter { apps.contains(it.packageName) }
    }

    /** Gets the list of locked systems apps whose data has been cleared by the user. */
    suspend fun getLockedDataClearedSystemApps(): List<String> {
        val systemLockedApps = getLockedApps().filter { it.isBundledApp }
        val dataClearedSystemApps = mutableListOf<String>()
        for (app in systemLockedApps) {
            val stats =
                storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    app.packageName,
                    Process.myUserHandle(),
                )
            if (stats.dataBytes <= DEFAULT_APP_SIZE) {
                dataClearedSystemApps.add(app.packageName)
            }
        }
        return dataClearedSystemApps
    }

    private companion object {
        const val DEFAULT_APP_SIZE = 24576L // Empty directory metadata size
        val logger = Logger(PinLockViewModel::class.java)
    }
}
