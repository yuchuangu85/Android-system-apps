/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.deskclock

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Timer
import com.android.deskclock.provider.Alarm

/**
 * DialogFragment to edit label.
 */
class LabelDialogFragment : DialogFragment() {
    private var mLabelBox: AppCompatEditText? = null
    private var mAlarm: Alarm? = null
    private var mTimerId = 0
    private var mTag: String? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // As long as the label box exists, save its state.
        mLabelBox?. let {
            outState.putString(ARG_LABEL, it.getText().toString())
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments ?: Bundle.EMPTY
        mAlarm = args.getParcelable(ARG_ALARM)
        mTimerId = args.getInt(ARG_TIMER_ID, -1)
        mTag = args.getString(ARG_TAG)

        var label = args.getString(ARG_LABEL)
        savedInstanceState?.let {
            label = it.getString(ARG_LABEL, label)
        }

        val dialog: AlertDialog = AlertDialog.Builder(requireActivity())
                .setPositiveButton(android.R.string.ok, OkListener())
                .setNegativeButton(android.R.string.cancel, null)
                .setMessage(R.string.label)
                .create()
        val context: Context = dialog.context

        val colorControlActivated = ThemeUtils.resolveColor(context, R.attr.colorControlActivated)
        val colorControlNormal = ThemeUtils.resolveColor(context, R.attr.colorControlNormal)

        mLabelBox = AppCompatEditText(context)
        mLabelBox?.setSupportBackgroundTintList(ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf()),
                intArrayOf(colorControlActivated, colorControlNormal)))
        mLabelBox?.setOnEditorActionListener(ImeDoneListener())
        mLabelBox?.addTextChangedListener(TextChangeListener())
        mLabelBox?.setSingleLine()
        mLabelBox?.setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        mLabelBox?.setText(label)
        mLabelBox?.selectAll()

        // The line at the bottom of EditText is part of its background therefore the padding
        // must be added to its container.
        val padding = context.resources
                .getDimensionPixelSize(R.dimen.label_edittext_padding)
        dialog.setView(mLabelBox, padding, 0, padding, 0)

        val alertDialogWindow: Window? = dialog.window
        alertDialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Stop callbacks from the IME since there is no view to process them.
        mLabelBox?.setOnEditorActionListener(null)
    }

    /**
     * Sets the new label into the timer or alarm.
     */
    private fun setLabel() {
        var label: String = mLabelBox!!.getText().toString()
        if (label.trim { it <= ' ' }.isEmpty()) {
            // Don't allow user to input label with only whitespace.
            label = ""
        }

        if (mAlarm != null) {
            (activity as AlarmLabelDialogHandler).onDialogLabelSet(mAlarm!!, label, mTag!!)
        } else if (mTimerId >= 0) {
            val timer: Timer? = DataModel.dataModel.getTimer(mTimerId)
            if (timer != null) {
                DataModel.dataModel.setTimerLabel(timer, label)
            }
        }
    }

    interface AlarmLabelDialogHandler {
        fun onDialogLabelSet(alarm: Alarm, label: String, tag: String)
    }

    /**
     * Alters the UI to indicate when input is valid or invalid.
     */
    private inner class TextChangeListener : TextWatcher {
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            mLabelBox?.setActivated(!TextUtils.isEmpty(s))
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun afterTextChanged(editable: Editable) {
        }
    }

    /**
     * Handles completing the label edit from the IME keyboard.
     */
    private inner class ImeDoneListener : OnEditorActionListener {
        override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                setLabel()
                dismissAllowingStateLoss()
                return true
            }
            return false
        }
    }

    /**
     * Handles completing the label edit from the Ok button of the dialog.
     */
    private inner class OkListener : DialogInterface.OnClickListener {
        override fun onClick(dialog: DialogInterface, which: Int) {
            setLabel()
            dismiss()
        }
    }

    companion object {
        /**
         * The tag that identifies instances of LabelDialogFragment in the fragment manager.
         */
        private const val TAG = "label_dialog"

        private const val ARG_LABEL = "arg_label"
        private const val ARG_ALARM = "arg_alarm"
        private const val ARG_TIMER_ID = "arg_timer_id"
        private const val ARG_TAG = "arg_tag"

        fun newInstance(alarm: Alarm, label: String?, tag: String?): LabelDialogFragment {
            val args = Bundle()
            args.putString(ARG_LABEL, label)
            args.putParcelable(ARG_ALARM, alarm)
            args.putString(ARG_TAG, tag)

            val frag = LabelDialogFragment()
            frag.arguments = args
            return frag
        }

        @JvmStatic
        fun newInstance(timer: Timer): LabelDialogFragment {
            val args = Bundle()
            args.putString(ARG_LABEL, timer.label)
            args.putInt(ARG_TIMER_ID, timer.id)

            val frag = LabelDialogFragment()
            frag.arguments = args
            return frag
        }

        /**
         * Replaces any existing LabelDialogFragment with the given `fragment`.
         */
        @JvmStatic
        fun show(manager: FragmentManager?, fragment: LabelDialogFragment) {
            if (manager == null || manager.isDestroyed) {
                return
            }

            // Finish any outstanding fragment work.
            manager.executePendingTransactions()

            val tx = manager.beginTransaction()

            // Remove existing instance of LabelDialogFragment if necessary.
            val existing = manager.findFragmentByTag(TAG)
            existing?.let {
                tx.remove(it)
            }
            tx.addToBackStack(null)

            fragment.show(tx, TAG)
        }
    }
}