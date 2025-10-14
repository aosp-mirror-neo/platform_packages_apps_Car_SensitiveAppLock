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
package com.android.car.sensitiveapplock.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.android.car.sensitiveapplock.AppLockData
import com.android.car.sensitiveapplock.data.ProtoSerializer
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope

/** A dagger module to provide a [DataStore] for AppLockData. */
@Module
@InstallIn(SingletonComponent::class)
object AppLockDataStoreModule {
    private const val APP_LOCK_DATA = "app_lock_data.pb"

    @Provides
    @Singleton
    fun provideAppLockDataStore(
        @ApplicationContext context: Context,
        @BackgroundContext backgroundContext: CoroutineContext,
    ): DataStore<AppLockData> {
        return DataStoreFactory.create(
            serializer = ProtoSerializer(AppLockData.getDefaultInstance()),
            corruptionHandler = ReplaceFileCorruptionHandler { AppLockData.getDefaultInstance() },
            scope = CoroutineScope(backgroundContext),
            produceFile = { File(context.filesDir, APP_LOCK_DATA) }
        )
    }
}
