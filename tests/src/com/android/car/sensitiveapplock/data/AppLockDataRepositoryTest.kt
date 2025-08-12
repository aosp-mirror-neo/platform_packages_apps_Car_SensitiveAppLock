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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.car.sensitiveapplock.RecoveryAccount
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@SmallTest
@RunWith(AndroidJUnit4::class)
class AppLockDataRepositoryTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appLockDataRepository: AppLockDataRepository

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun setPin_updatesData() = runTest {
        appLockDataRepository.setPin(USER_PIN)
        assertThat(appLockDataRepository.getPin()).isEqualTo(USER_PIN)
    }

    @Test
    fun addLockedApp_updatesData() = runTest {
        appLockDataRepository.addLockedApp(LOCKED_APP)
        assertThat(appLockDataRepository.getLockedApps()).containsExactly(LOCKED_APP)
    }

    @Test
    fun removeLockedApp_updatesData() = runTest {
        appLockDataRepository.apply {
            addLockedApp(LOCKED_APP)
            removeLockedApp(LOCKED_APP)
        }

        assertThat(appLockDataRepository.getLockedApps()).isEmpty()
    }

    @Test
    fun clearData_removesAllData() = runTest {
        appLockDataRepository.apply {
            setPin(USER_PIN)
            addLockedApp(LOCKED_APP)
            setReAuthPinRecoveryAccount(RECOVERY_ACCOUNT)
            addLockedDataClearedSystemApp(LOCKED_APP)
            clearData()
        }

        assertThat(appLockDataRepository.getPin()).isEmpty()
        assertThat(appLockDataRepository.getLockedApps()).isEmpty()
        assertThat(appLockDataRepository.reAuthPinRecoveryEnabled()).isFalse()
        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    @Test
    fun getPin_whenUnset_returnsEmptyString() = runTest {
        assertThat(appLockDataRepository.getPin()).isEmpty()
    }

    @Test
    fun getLockedApps_whenUnset_returnsEmptyList() = runTest {
        assertThat(appLockDataRepository.getLockedApps()).isEmpty()
    }

    @Test
    fun setReAuthPinRecoveryAccount_updatesData() = runTest {
        appLockDataRepository.setReAuthPinRecoveryAccount(RECOVERY_ACCOUNT)

        val recoveryAccount = appLockDataRepository.getReAuthPinRecoveryAccount()
        assertThat(appLockDataRepository.reAuthPinRecoveryEnabled()).isTrue()
        assertThat(recoveryAccount.name).isEqualTo(RECOVERY_ACCOUNT.name)
        assertThat(recoveryAccount.type).isEqualTo(RECOVERY_ACCOUNT.type)
    }

    @Test
    fun clearReAuthPinRecoveryAccount_updatesData() = runTest {
        appLockDataRepository.setReAuthPinRecoveryAccount(RECOVERY_ACCOUNT)

        appLockDataRepository.clearReAuthPinRecoveryAccount()
        assertThat(appLockDataRepository.reAuthPinRecoveryEnabled()).isFalse()
        assertThat(appLockDataRepository.getReAuthPinRecoveryAccount())
            .isEqualTo(RecoveryAccount.getDefaultInstance())
    }

    @Test
    fun reAuthPinRecoveryEnabled_whenNotEnabled_returnsFalse() = runTest {
        assertThat(appLockDataRepository.reAuthPinRecoveryEnabled()).isFalse()
    }

    @Test
    fun addLockedDataClearedSystemApp_updatesData() = runTest {
        appLockDataRepository.addLockedDataClearedSystemApp(LOCKED_APP)
        assertThat(appLockDataRepository.getLockedDataClearedSystemApps())
            .containsExactly(LOCKED_APP)
    }

    @Test
    fun clearLockedDataClearedSystemApps_updatesData() = runTest {
        appLockDataRepository.apply {
            addLockedDataClearedSystemApp(LOCKED_APP)
            addLockedDataClearedSystemApp(LOCKED_APP)
            clearLockedDataClearedSystemApps()
        }

        assertThat(appLockDataRepository.getLockedDataClearedSystemApps()).isEmpty()
    }

    private companion object {
        const val USER_PIN = "7355608"
        const val LOCKED_APP = "com.trip.to.sweden"
        const val RECOVERY_ACCOUNT_NAME = "Gothenburg"
        const val RECOVERY_ACCOUNT_TYPE = "com.sweden"
        val RECOVERY_ACCOUNT = Account(RECOVERY_ACCOUNT_NAME, RECOVERY_ACCOUNT_TYPE)
    }
}
