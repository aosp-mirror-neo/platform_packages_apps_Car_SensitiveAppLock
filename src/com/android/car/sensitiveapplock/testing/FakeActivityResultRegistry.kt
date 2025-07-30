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

package com.android.car.sensitiveapplock.testing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat

/**
 * A fake [ActivityResultRegistry] that allows returning a specific result code for all requests.
 */
class FakeActivityResultRegistry(private val context: Context) : ActivityResultRegistry() {
    private var lastLaunchedIntent: Intent? = null
    private val resultCodes = mutableListOf<Int>()
    private val resultBundles = mutableListOf<Bundle>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        lastLaunchedIntent = contract.createIntent(context, input)
        val bundle = resultBundles.removeFirstOrNull() ?: Bundle.EMPTY
        val intent = Intent().apply { putExtras(bundle) }
        val resultCode = resultCodes.removeFirstOrNull() ?: Activity.RESULT_CANCELED
        dispatchResult(requestCode, ActivityResult(resultCode, intent))
    }

    fun getLastLaunchedIntent(): Intent? = lastLaunchedIntent

    fun setResult(resultCode: Int, bundle: Bundle = Bundle.EMPTY) {
        resultCodes.add(resultCode)
        resultBundles.add(bundle)
    }
}
