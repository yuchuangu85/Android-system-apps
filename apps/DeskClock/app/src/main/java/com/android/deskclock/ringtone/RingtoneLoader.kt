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

import android.content.Context
import android.database.MatrixCursor
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import androidx.loader.content.AsyncTaskLoader

import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.data.CustomRingtone
import com.android.deskclock.data.DataModel

/**
 * Assembles the list of ItemHolders that back the RecyclerView used to choose a ringtone.
 */
internal class RingtoneLoader(
    context: Context,
    private val mDefaultRingtoneUri: Uri,
    private val mDefaultRingtoneTitle: String
) : AsyncTaskLoader<List<ItemHolder<Uri?>>>(context) {
    private var mCustomRingtones: List<CustomRingtone>? = null

    override fun onStartLoading() {
        super.onStartLoading()

        mCustomRingtones = DataModel.dataModel.customRingtones
        forceLoad()
    }

    override fun loadInBackground(): List<ItemHolder<Uri?>> {
        // Prime the ringtone title cache for later access.
        DataModel.dataModel.loadRingtoneTitles()
        DataModel.dataModel.loadRingtonePermissions()

        // Fetch the standard system ringtones.
        val ringtoneManager = RingtoneManager(context)
        ringtoneManager.setType(AudioManager.STREAM_ALARM)

        val systemRingtoneCursor = try {
            ringtoneManager.cursor
        } catch (e: Exception) {
            LogUtils.e("Could not get system ringtone cursor")
            MatrixCursor(arrayOf())
        }
        val systemRingtoneCount = systemRingtoneCursor.count
        // item count = # system ringtones + # custom ringtones + 2 headers + Add new music item
        val itemCount = systemRingtoneCount + mCustomRingtones!!.size + 3

        val itemHolders: MutableList<ItemHolder<Uri?>> = ArrayList(itemCount)

        // Add the item holder for the Music heading.
        itemHolders.add(HeaderHolder(R.string.your_sounds))

        // Add an item holder for each custom ringtone and also cache a pretty name.
        for (ringtone in mCustomRingtones!!) {
            itemHolders.add(CustomRingtoneHolder(ringtone))
        }

        // Add an item holder for the "Add new" music ringtone.
        itemHolders.add(AddCustomRingtoneHolder())

        // Add an item holder for the Ringtones heading.
        itemHolders.add(HeaderHolder(R.string.device_sounds))

        // Add an item holder for the silent ringtone.
        itemHolders.add(SystemRingtoneHolder(Utils.RINGTONE_SILENT, null))

        // Add an item holder for the system default alarm sound.
        itemHolders.add(SystemRingtoneHolder(mDefaultRingtoneUri, mDefaultRingtoneTitle))

        // Add an item holder for each system ringtone.
        for (i in 0 until systemRingtoneCount) {
            val ringtoneUri = ringtoneManager.getRingtoneUri(i)
            itemHolders.add(SystemRingtoneHolder(ringtoneUri, null))
        }

        return itemHolders
    }

    override fun onReset() {
        super.onReset()
        mCustomRingtones = null
    }
}