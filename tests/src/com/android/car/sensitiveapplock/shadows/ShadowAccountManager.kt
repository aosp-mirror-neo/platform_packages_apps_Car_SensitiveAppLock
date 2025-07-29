/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.sensitiveapplock.shadows

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManager.KEY_ACCOUNT_NAME
import android.accounts.AccountManager.KEY_ACCOUNT_TYPE
import android.accounts.AccountManager.KEY_BOOLEAN_RESULT
import android.accounts.AccountManager.KEY_INTENT
import android.accounts.AccountManager.KEY_LAST_AUTHENTICATED_TIME
import android.accounts.AccountManager.KEY_PASSWORD
import android.accounts.AccountManagerCallback
import android.accounts.AccountManagerFuture
import android.accounts.AuthenticatorException
import android.accounts.OperationCanceledException
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Shadow of [AccountManager]. */
@Implements(AccountManager::class)
class ShadowAccountManager : org.robolectric.shadows.ShadowAccountManager() {
    @Implementation
    fun confirmCredentials(
        account: Account?,
        options: Bundle?,
        activity: Activity?,
        callback: AccountManagerCallback<Bundle>?,
        handler: Handler?,
    ): AccountManagerFuture<Bundle> {
        if (account == null) {
            throw IllegalArgumentException("account is null")
        }
        val future = RoboAccountManagerFuture(account, options, activity, callback, handler)
        future.start()
        return future
    }

    private inner class RoboAccountManagerFuture(
        private val account: Account,
        private val options: Bundle?,
        private val activity: Activity?,
        private val callback: AccountManagerCallback<Bundle>?,
        private val handler: Handler?,
    ) : AccountManagerFuture<Bundle> {
        private var result = Bundle()
        private var exception: Exception? = null
        private var started = false

        fun start() {
            if (started) {
                return
            }
            started = true
            try {
                result = doWork()
            } catch (e: OperationCanceledException) {
                exception = e
            } catch (e: IOException) {
                exception = e
            } catch (e: AuthenticatorException) {
                exception = e
            }
            if (callback != null) {
                val mainHandler = handler ?: Handler(Looper.getMainLooper())
                mainHandler.post { callback.run { this } }
            }
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun isCancelled(): Boolean = false

        override fun isDone(): Boolean {
            return result != Bundle.EMPTY || exception != null || isCancelled()
        }

        override fun getResult(): Bundle? {
            start()
            when (exception) {
                is OperationCanceledException -> throw OperationCanceledException()
                is IOException -> throw IOException()
                is AuthenticatorException -> throw AuthenticatorException()
            }
            return result
        }

        override fun getResult(timeout: Long, unit: TimeUnit?): Bundle? = getResult()

        fun doWork(): Bundle {
            val authenticator = authenticatorTypes.find { it.type == account.type }
            if (authenticator == null) {
                throw AuthenticatorException()
            }
            if (activity == null || options?.getString(KEY_PASSWORD) == null) {
                result.putParcelable(KEY_INTENT, Intent())
            } else {
                result.putString(KEY_ACCOUNT_NAME, account.name)
                result.putString(KEY_ACCOUNT_TYPE, account.type)
                result.putBoolean(KEY_BOOLEAN_RESULT, true)
                result.putLong(KEY_LAST_AUTHENTICATED_TIME, -1)
            }
            return result
        }
    }
}
