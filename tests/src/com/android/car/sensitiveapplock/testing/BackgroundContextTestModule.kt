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
package com.android.car.sensitiveapplock.testing

import com.android.car.sensitiveapplock.di.BackgroundContextModule
import com.android.car.sensitiveapplock.di.qualifiers.BackgroundContext
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Module to replace [BackgroundContext] with a [CoroutineContext] that that isn't confined to any
 * specific thread. It executes in the current call-frame's thread and is used for testing.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [BackgroundContextModule::class],
)
object BackgroundContextTestModule {

    /** Provides an unconfined [CoroutineContext]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Reusable
    @BackgroundContext
    fun provideBackgroundContext(): CoroutineContext = UnconfinedTestDispatcher()
}
