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

package com.android.car.sensitiveapplock.settings

import android.database.Cursor
import android.database.MatrixCursor
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_ICON_RESID
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_CLASS
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY
import android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_TITLE
import android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS
import android.provider.SearchIndexablesProvider
import com.android.car.sensitiveapplock.R

/** Provider to add Sensitive App Lock in Android Settings search results. */
class SettingsSearchIndexablesProvider : SearchIndexablesProvider() {
    override fun queryXmlResources(projection: Array<out String?>?): Cursor? {
        return null
    }

    override fun queryRawData(projection: Array<out String?>?): Cursor? {
        val cursor = MatrixCursor(INDEXABLES_RAW_COLUMNS)
        val row = arrayOfNulls<Any>(INDEXABLES_RAW_COLUMNS.size)
        row[COLUMN_INDEX_RAW_KEY] = context?.getString(R.string.setting_title)
        row[COLUMN_INDEX_RAW_TITLE] = context?.getString(R.string.setting_title)
        row[COLUMN_INDEX_RAW_ICON_RESID] = R.drawable.setting_icon
        row[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = context?.applicationInfo?.packageName
        row[COLUMN_INDEX_RAW_INTENT_TARGET_CLASS] = SettingsActivity::class.qualifiedName

        cursor.addRow(row)
        return cursor
    }

    override fun queryNonIndexableKeys(projection: Array<out String?>?): Cursor? {
        return null
    }

    override fun onCreate(): Boolean {
        return true
    }
}
