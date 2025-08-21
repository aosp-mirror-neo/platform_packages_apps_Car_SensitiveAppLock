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

package com.android.car.sensitiveapplock.lockscreen

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.android.car.sensitiveapplock.R
import com.android.car.sensitiveapplock.data.AppInfo

/**
 * A [DialogFragment] for showing the list of apps that need to be uninstalled or cleared before the
 * user can reset their app lock pin.
 *
 * This is only shown to the user when they do not have a recovery account setup.
 */
class PinResetDialogFragment(lockedApps: List<AppInfo>, private val dataClearedApps: List<String>) :
    DialogFragment() {
    private val userApps: List<AppInfo>
    private val systemApps: List<AppInfo>
    private val systemAppsDataCleared: Boolean
    private lateinit var resetView: View
    private var startPinRecreateFlow = false

    init {
        val (systemApps, userApps) = lockedApps.partition { it.isBundledApp }
        this.systemApps = systemApps
        this.userApps = userApps
        this.systemAppsDataCleared = dataClearedApps.containsAll(systemApps.map { it.packageName })
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        resetView = layoutInflater.inflate(R.layout.pin_reset_view, null)

        if (userApps.isEmpty() && systemAppsDataCleared) {
            startPinRecreateFlow = true
            return showResetNowDialog()
        }
        return showAppsListDialog()
    }

    private fun showResetNowDialog(): Dialog {
        val dialog =
            AlertDialog.Builder(context)
                .apply {
                    setView(resetView)
                    setPositiveButton(R.string.reset_dialog_positive_button_text) { _, _ ->
                        onButtonClick()
                    }
                }
                .create()
        resetView.findViewById<TextView>(R.id.reset_dialog_title).text =
            getString(R.string.reset_dialog_reset_now_title)
        resetView.findViewById<TextView>(R.id.reset_dialog_message).text =
            getString(R.string.reset_dialog_reset_now_message)
        return dialog
    }

    private fun showAppsListDialog(): Dialog {
        val dialog =
            AlertDialog.Builder(context)
                .apply {
                    setView(resetView)
                    setNeutralButton(R.string.reset_dialog_neutral_button_text) { _, _ ->
                        onButtonClick()
                    }
                }
                .create()
        setUserLockableApps()
        setSystemLockableApps()
        return dialog
    }

    private fun setUserLockableApps() {
        if (userApps.isEmpty()) {
            return
        }
        val userAppsDrawable = userApps.map { it.icon }
        val message = resetView.findViewById<View>(R.id.reset_dialog_user_apps_message)
        val gridView = resetView.findViewById<GridView>(R.id.reset_dialog_user_apps_list)
        message.visibility = View.VISIBLE
        gridView.visibility = View.VISIBLE
        gridView.adapter = DrawableGridAdapter(requireContext(), userAppsDrawable)
    }

    private fun setSystemLockableApps() {
        if (systemAppsDataCleared) {
            return
        }
        // List of system app icons whose data is not cleared
        val userAppsDrawable =
            systemApps.filterNot { dataClearedApps.contains(it.packageName) }.map { it.icon }
        val message = resetView.findViewById<View>(R.id.reset_dialog_user_preinstalled_apps_message)
        val gridView =
            resetView.findViewById<GridView>(R.id.reset_dialog_user_preinstalled_apps_list)
        message.visibility = View.VISIBLE
        gridView.visibility = View.VISIBLE
        gridView.adapter = DrawableGridAdapter(requireContext(), userAppsDrawable)
    }

    private fun onButtonClick() {
        val result =
            Bundle().apply { putBoolean(PIN_RESET_DIALOG_BUNDLE_KEY, startPinRecreateFlow) }
        setFragmentResult(PIN_RESET_DIALOG_REQUEST_KEY, result)
    }

    /**
     * A [GridView] that expands to fit its content without becoming scrollable.
     *
     * This is used to display a list of app icons in the [PinResetDialogFragment].
     */
    class NonScrollableGridView : GridView {
        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        constructor(
            context: Context,
            attrs: AttributeSet,
            defStyle: Int,
        ) : super(context, attrs, defStyle)

        public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // We set a large height to allow the GridView to expand without becoming scrollable.
            val expandSpec = MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, expandSpec)
        }
    }

    private class DrawableGridAdapter(
        private val context: Context,
        private val drawables: List<Drawable>,
    ) : BaseAdapter() {
        override fun getCount(): Int = drawables.size

        override fun getItem(position: Int): Any? = drawables[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
            var imageView: ImageView?
            if (convertView == null) {
                imageView = ImageView(context)
                val imageSize =
                    context.resources.getDimensionPixelSize(R.dimen.reset_dialog_app_icon_size)
                val padding =
                    context.resources.getDimensionPixelSize(R.dimen.reset_dialog_app_icon_padding)
                imageView.layoutParams =
                    GridLayout.LayoutParams().apply {
                        height = imageSize
                        width = imageSize
                    }
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setPadding(padding)
            } else {
                imageView = convertView as ImageView
            }
            imageView.setImageDrawable(drawables[position])
            return imageView
        }
    }

    companion object {
        const val TAG = "PIN_RESET_DIALOG_FRAGMENT_TAG"
        const val PIN_RESET_DIALOG_REQUEST_KEY = "user_pin_reset_dialog_request_key"
        const val PIN_RESET_DIALOG_BUNDLE_KEY = "user_pin_reset_dialog_bundle_key"
    }
}
