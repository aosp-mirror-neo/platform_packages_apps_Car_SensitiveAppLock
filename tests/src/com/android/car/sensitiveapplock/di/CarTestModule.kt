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

import android.car.Car
import android.car.hardware.power.CarPowerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/** Test Module to provide mock car-lib dependencies. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CarModule::class])
internal object CarTestModule {
    @Provides
    @Singleton
    fun provideMockCar(): Car {
        val carPowerManager = mock<CarPowerManager>()
        return mock<Car> {
            on { getCarManager(CarPowerManager::class.java) } doReturn carPowerManager
        }
    }
}
