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

package com.android.deskclock.ringtone

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.R

internal class AddCustomRingtoneViewHolder private constructor(itemView: View)
    : ItemViewHolder<AddCustomRingtoneHolder>(itemView), View.OnClickListener {

    init {
        itemView.setOnClickListener(this)
        val selectedView = itemView.findViewById<View>(R.id.sound_image_selected)
        selectedView.visibility = View.GONE
        val nameView = itemView.findViewById<View>(R.id.ringtone_name) as TextView
        nameView.text = itemView.context.getString(R.string.add_new_sound)
        nameView.alpha = 0.63f
        val imageView = itemView.findViewById<View>(R.id.ringtone_image) as ImageView
        imageView.setImageResource(R.drawable.ic_add_white_24dp)
        imageView.alpha = 0.63f
    }

    override fun onClick(view: View) {
        notifyItemClicked(CLICK_ADD_NEW)
    }

    class Factory internal constructor(private val mInflater: LayoutInflater)
        : ItemViewHolder.Factory {
        override fun createViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<*> {
            val itemView =
                    mInflater.inflate(R.layout.ringtone_item_sound, parent, false)
            return AddCustomRingtoneViewHolder(itemView)
        }
    }

    companion object {
        const val VIEW_TYPE_ADD_NEW = Int.MIN_VALUE
        const val CLICK_ADD_NEW = VIEW_TYPE_ADD_NEW
    }
}