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

import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher

/** Module to provide a background [CoroutineContext]. */
@Module
@InstallIn(SingletonComponent::class)
object BackgroundContextModule {
    private const val BACKGROUND_POOL_SIZE = 4

    /** Provides a background-thread [CoroutineContext]. */
    @Provides
    @Reusable
    @BackgroundContext
    fun provideBackgroundContext(): CoroutineContext {
        return Executors.newFixedThreadPool(BACKGROUND_POOL_SIZE).asCoroutineDispatcher()
    }
}
