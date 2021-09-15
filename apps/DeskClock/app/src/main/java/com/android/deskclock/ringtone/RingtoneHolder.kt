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

import android.net.Uri
import androidx.recyclerview.widget.RecyclerView.NO_ID

import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel

internal abstract class RingtoneHolder @JvmOverloads constructor(
    uri: Uri,
    private val mName: String?,
    private val mHasPermissions: Boolean = true
) : ItemHolder<Uri?>(uri, NO_ID) {
    var isSelected = false
    var isPlaying = false

    val id: Long
        get() = itemId

    fun hasPermissions(): Boolean {
        return mHasPermissions
    }

    val uri: Uri
        get() = item!!

    val isSilent: Boolean
        get() = Utils.RINGTONE_SILENT == uri

    val name: String?
        get() = mName ?: DataModel.dataModel.getRingtoneTitle(uri)
}