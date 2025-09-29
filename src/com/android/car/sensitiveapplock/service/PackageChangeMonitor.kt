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

package com.android.car.sensitiveapplock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import com.android.car.sensitiveapplock.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors for package changes, specifically when new packages are added to the system.
 *
 * This class registers a [BroadcastReceiver] to listen for [Intent.ACTION_PACKAGE_ADDED] broadcast.
 */
@Singleton
open class PackageChangeMonitor @Inject constructor(@ApplicationContext val context: Context) {
    private val listeners = mutableSetOf<Listener>()
    private val receiver =
        object : BroadcastReceiver() {
            @Synchronized
            override fun onReceive(ctx: Context, intent: Intent) {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == null) {
                    logger.w("Received null package name.")
                    return
                }

                when (intent.action) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        for (listener in listeners) {
                            listener.onPackageAdded(packageName)
                        }
                    }
                    else -> logger.v("Unhandled intent action:${intent.action}")
                }
            }
        }

    /** Adds a [Listener] to be notified of package changes. */
    @Synchronized
    open fun addListener(listener: Listener) {
        val wasEmpty = listeners.isEmpty()
        if (listeners.add(listener) && wasEmpty) {
            registerReceiver()
        }
    }

    /** Removes a [Listener] from being notified of package changes. */
    @Synchronized
    open fun removeListener(listener: Listener) {
        if (listeners.remove(listener) && listeners.isEmpty()) {
            unregisterReceiver()
        }
    }

    private fun registerReceiver() {
        val intentFilter =
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        context.registerReceiver(receiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
    }

    /** Interface for listeners interested in package change events. */
    interface Listener {
        /** Called when a new package has been installed on the system. */
        fun onPackageAdded(packageName: String) {}
    }

    private companion object {
        val logger = Logger(PackageChangeMonitor::class.java)
    }
}
