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
package com.android.car.sensitiveapplock.data

import android.accounts.Account
import androidx.datastore.core.DataStore
import com.android.car.sensitiveapplock.AppLockData
import com.android.car.sensitiveapplock.RecoveryAccount
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** A repository for accessing a user's App Lock data. */
class AppLockDataRepository @Inject constructor(private val dataStore: DataStore<AppLockData>) {
    /** A [Flow] of the current value of the data store. */
    val appLockDataFlow: Flow<AppLockData> =
        dataStore.data.catch { exception ->
            if (exception is IOException) {
                emit(AppLockData.getDefaultInstance())
            } else {
                throw exception
            }
        }

    /** Gets the user's PIN. */
    suspend fun getPin(): String = dataStore.data.first().password

    /** Gets the user's list of locked apps. */
    suspend fun getLockedApps(): List<String> = dataStore.data.first().lockedAppsList

    /** Sets the user's PIN. */
    suspend fun setPin(pin: String) {
        dataStore.updateData { appLockData -> appLockData.toBuilder().setPassword(pin).build() }
    }

    /** Adds an app to the user's list of locked apps. */
    suspend fun addLockedApp(packageName: String) {
        dataStore.updateData { appLockData ->
            appLockData.toBuilder().addLockedApps(packageName).build()
        }
    }

    /** Removes an app from the user's list of locked apps. */
    suspend fun removeLockedApp(packageName: String) {
        dataStore.updateData { appLockData ->
            val lockedApps = ArrayList<String>(appLockData.lockedAppsList)
            lockedApps.remove(packageName)
            appLockData.toBuilder().clearLockedApps().addAllLockedApps(lockedApps).build()
        }
    }

    /** Sets the reAuth pin recovery account. */
    suspend fun setReAuthPinRecoveryAccount(account: Account) {
        val recoveryAccount =
            RecoveryAccount.newBuilder().setName(account.name).setType(account.type).build()
        dataStore.updateData { appLockData ->
            appLockData.toBuilder().setReAuthRecoveryAccount(recoveryAccount).build()
        }
    }

    /** Returns true if pin recovery via reAuth is enabled, false otherwise. */
    suspend fun reAuthPinRecoveryEnabled(): Boolean {
        return dataStore.data.first().reAuthRecoveryAccount != RecoveryAccount.getDefaultInstance()
    }

    /** Clears the user's App Lock data. */
    suspend fun clearData() {
        dataStore.updateData { appLockData -> AppLockData.getDefaultInstance() }
    }
}
