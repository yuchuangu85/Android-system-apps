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
 * limitations under the License.
 */

package com.android.deskclock.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.preference.DropDownPreference

import com.android.deskclock.R
import com.android.deskclock.Utils

/**
 * Bend [DropDownPreference] to support
 * [Simple Menus](https://material.google.com/components/menus.html#menus-behavior).
 */
class SimpleMenuPreference(
    context: Context?,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int
) : DropDownPreference(context, attrs, defStyleAttr, defStyleRes) {
    private lateinit var mAdapter: SimpleMenuAdapter

    constructor(context: Context?) : this(context, null) {
    }

    constructor(context: Context?, attrs: AttributeSet?) :
            this(context, attrs, R.attr.dropdownPreferenceStyle) {
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) :
            this(context, attrs, defStyle, 0) {
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(getContext(), R.layout.simple_menu_dropdown_item)
        return mAdapter
    }

    override fun setSummary(summary: CharSequence) {
        val entries: Array<CharSequence> = getEntries()
        val index = Utils.indexOf(entries, summary)
        require(index != -1) { "Illegal Summary" }
        val lastSelectedOriginalPosition = mAdapter.lastSelectedOriginalPosition
        mAdapter.setSelectedPosition(index)
        setSelectedPosition(entries, lastSelectedOriginalPosition, index)
        setSelectedPosition(getEntryValues(), lastSelectedOriginalPosition, index)
        super.setSummary(summary)
    }

    private class SimpleMenuAdapter internal constructor(context: Context, resource: Int) :
            ArrayAdapter<CharSequence?>(context, resource) {

        /** The original position of the last selected element  */
        var lastSelectedOriginalPosition = 0
            private set

        private fun restoreOriginalOrder() {
            val item: CharSequence? = getItem(0)
            remove(item)
            insert(item, lastSelectedOriginalPosition)
        }

        private fun swapSelectedToFront(position: Int) {
            val item: CharSequence? = getItem(position)
            remove(item)
            insert(item, 0)
            lastSelectedOriginalPosition = position
        }

        fun setSelectedPosition(position: Int) {
            setNotifyOnChange(false)
            val item: CharSequence? = getItem(position)
            restoreOriginalOrder()
            val originalPosition: Int = getPosition(item)
            swapSelectedToFront(originalPosition)
            notifyDataSetChanged()
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View = super.getDropDownView(position, convertView, parent)
            if (position == 0) {
                view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.white_08p))
            } else {
                view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.transparent))
            }
            return view
        }
    }

    companion object {
        private fun restoreOriginalOrder(
            array: Array<CharSequence>,
            lastSelectedOriginalPosition: Int
        ) {
            val item = array[0]
            System.arraycopy(array, 1, array, 0, lastSelectedOriginalPosition)
            array[lastSelectedOriginalPosition] = item
        }

        private fun swapSelectedToFront(array: Array<CharSequence>, position: Int) {
            val item = array[position]
            System.arraycopy(array, 0, array, 1, position)
            array[0] = item
        }

        private fun setSelectedPosition(
            array: Array<CharSequence>,
            lastSelectedOriginalPosition: Int,
            position: Int
        ) {
            val item = array[position]
            restoreOriginalOrder(array, lastSelectedOriginalPosition)
            val originalPosition = Utils.indexOf(array, item)
            swapSelectedToFront(array, originalPosition)
        }
    }
}