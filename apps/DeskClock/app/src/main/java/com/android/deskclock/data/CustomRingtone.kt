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

package com.android.deskclock.data

import android.net.Uri

/**
 * A read-only domain object representing a custom ringtone chosen from the file system.
 */
class CustomRingtone internal constructor(
    /** The unique identifier of the custom ringtone.  */
    val id: Long,
    /** The uri that allows playback of the ringtone.  */
    private val mUri: Uri,
    /** The title describing the file at the given uri; typically the file name.  */
    val title: String?,
    /** `true` iff the application has permission to read the content of `mUri uri`.  */
    private val mHasPermissions: Boolean
) : Comparable<CustomRingtone> {

    val uri: Uri
        get() = mUri

    fun hasPermissions(): Boolean = mHasPermissions

    fun setHasPermissions(hasPermissions: Boolean): CustomRingtone =
            if (mHasPermissions == hasPermissions) {
                this
            } else {
                CustomRingtone(id, mUri, title, hasPermissions)
            }

    override fun compareTo(other: CustomRingtone): Int {
        return String.CASE_INSENSITIVE_ORDER.compare(title, other.title)
    }
}