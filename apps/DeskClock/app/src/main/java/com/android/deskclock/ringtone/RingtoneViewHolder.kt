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

import android.graphics.PorterDuff
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ContextMenu.ContextMenuInfo
import android.view.View.OnCreateContextMenuListener
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

import com.android.deskclock.AnimatorUtils
import com.android.deskclock.ItemAdapter.ItemViewHolder
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils

internal class RingtoneViewHolder private constructor(itemView: View)
    : ItemViewHolder<RingtoneHolder>(itemView), View.OnClickListener, OnCreateContextMenuListener {
    private val mSelectedView: View = itemView.findViewById(R.id.sound_image_selected)
    private val mNameView: TextView = itemView.findViewById<View>(R.id.ringtone_name) as TextView
    private val mImageView: ImageView =
            itemView.findViewById<View>(R.id.ringtone_image) as ImageView

    init {
        itemView.setOnClickListener(this)
    }

    override fun onBindItemView(itemHolder: RingtoneHolder) {
        mNameView.text = itemHolder.name
        val opaque = itemHolder.isSelected || !itemHolder.hasPermissions()
        mNameView.alpha = if (opaque) 1f else .63f
        mImageView.alpha = if (opaque) 1f else .63f
        mImageView.clearColorFilter()

        val itemViewType: Int = getItemViewType()
        if (itemViewType == VIEW_TYPE_CUSTOM_SOUND) {
            if (!itemHolder.hasPermissions()) {
                mImageView.setImageResource(R.drawable.ic_ringtone_not_found)
                val colorAccent = ThemeUtils.resolveColor(itemView.getContext(),
                        R.attr.colorAccent)
                mImageView.setColorFilter(colorAccent, PorterDuff.Mode.SRC_ATOP)
            } else {
                mImageView.setImageResource(R.drawable.placeholder_album_artwork)
            }
        } else if (itemHolder.item == Utils.RINGTONE_SILENT) {
            mImageView.setImageResource(R.drawable.ic_ringtone_silent)
        } else if (itemHolder.isPlaying) {
            mImageView.setImageResource(R.drawable.ic_ringtone_active)
        } else {
            mImageView.setImageResource(R.drawable.ic_ringtone)
        }
        AnimatorUtils.startDrawableAnimation(mImageView)

        mSelectedView.visibility = if (itemHolder.isSelected) View.VISIBLE else View.GONE

        val bgColorId = if (itemHolder.isSelected) R.color.white_08p else R.color.transparent
        itemView.setBackgroundColor(ContextCompat.getColor(itemView.getContext(), bgColorId))

        if (itemViewType == VIEW_TYPE_CUSTOM_SOUND) {
            itemView.setOnCreateContextMenuListener(this)
        }
    }

    override fun onClick(view: View) {
        if (itemHolder!!.hasPermissions()) {
            notifyItemClicked(CLICK_NORMAL)
        } else {
            notifyItemClicked(CLICK_NO_PERMISSIONS)
        }
    }

    override fun onCreateContextMenu(
        contextMenu: ContextMenu,
        view: View,
        contextMenuInfo: ContextMenuInfo
    ) {
        notifyItemClicked(CLICK_LONG_PRESS)
        contextMenu.add(Menu.NONE, 0, Menu.NONE, R.string.remove_sound)
    }

    class Factory internal constructor(private val mInflater: LayoutInflater)
        : ItemViewHolder.Factory {
        override fun createViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<*> {
            val itemView = mInflater.inflate(R.layout.ringtone_item_sound, parent, false)
            return RingtoneViewHolder(itemView)
        }
    }

    companion object {
        const val VIEW_TYPE_SYSTEM_SOUND = R.layout.ringtone_item_sound
        const val VIEW_TYPE_CUSTOM_SOUND = -R.layout.ringtone_item_sound
        const val CLICK_NORMAL = 0
        const val CLICK_LONG_PRESS = -1
        const val CLICK_NO_PERMISSIONS = -2
    }
}