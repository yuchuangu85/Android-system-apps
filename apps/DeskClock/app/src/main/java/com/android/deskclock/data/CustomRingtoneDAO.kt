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

import android.content.SharedPreferences
import android.net.Uri

/**
 * This class encapsulates the transfer of data between [CustomRingtone] domain objects and
 * their permanent storage in [SharedPreferences].
 */
internal object CustomRingtoneDAO {
    /** Key to a preference that stores the set of all custom ringtone ids.  */
    private const val RINGTONE_IDS = "ringtone_ids"

    /** Key to a preference that stores the next unused ringtone id.  */
    private const val NEXT_RINGTONE_ID = "next_ringtone_id"

    /** Prefix for a key to a preference that stores the URI associated with the ringtone id.  */
    private const val RINGTONE_URI = "ringtone_uri_"

    /** Prefix for a key to a preference that stores the title associated with the ringtone id.  */
    private const val RINGTONE_TITLE = "ringtone_title_"

    /**
     * @param uri points to an audio file located on the file system
     * @param title the title of the audio content at the given `uri`
     * @return the newly added custom ringtone
     */
    fun addCustomRingtone(prefs: SharedPreferences, uri: Uri, title: String?): CustomRingtone {
        val id: Long = prefs.getLong(NEXT_RINGTONE_ID, 0)
        val ids = getRingtoneIds(prefs)
        ids.add(id.toString())

        prefs.edit()
                .putString(RINGTONE_URI + id, uri.toString())
                .putString(RINGTONE_TITLE + id, title)
                .putLong(NEXT_RINGTONE_ID, id + 1)
                .putStringSet(RINGTONE_IDS, ids)
                .apply()

        return CustomRingtone(id, uri, title, true)
    }

    /**
     * @param id identifies the ringtone to be removed
     */
    fun removeCustomRingtone(prefs: SharedPreferences, id: Long) {
        val ids = getRingtoneIds(prefs)
        ids.remove(id.toString())

        val editor: SharedPreferences.Editor = prefs.edit()
        editor.apply {
            remove(RINGTONE_URI + id)
            remove(RINGTONE_TITLE + id)
            if (ids.isEmpty()) {
                remove(RINGTONE_IDS)
                remove(NEXT_RINGTONE_ID)
            } else {
                putStringSet(RINGTONE_IDS, ids)
            }
            apply()
        }
    }

    /**
     * @return a list of all known custom ringtones
     */
    fun getCustomRingtones(prefs: SharedPreferences): MutableList<CustomRingtone> {
        val ids: Set<String> = prefs.getStringSet(RINGTONE_IDS, emptySet<String>())!!
        val ringtones: MutableList<CustomRingtone> = ArrayList(ids.size)

        for (id in ids) {
            val idLong = id.toLong()
            val uri: Uri = Uri.parse(prefs.getString(RINGTONE_URI + id, null))
            val title: String? = prefs.getString(RINGTONE_TITLE + id, null)
            ringtones.add(CustomRingtone(idLong, uri, title, true))
        }

        return ringtones
    }

    private fun getRingtoneIds(prefs: SharedPreferences): MutableSet<String> {
        return prefs.getStringSet(RINGTONE_IDS, mutableSetOf<String>())!!
    }
}