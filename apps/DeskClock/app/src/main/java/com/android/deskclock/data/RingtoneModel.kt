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

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.UriPermission
import android.database.ContentObserver
import android.database.Cursor
import android.media.AudioManager.STREAM_ALARM
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.RingtoneManager.TITLE_COLUMN_INDEX
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.ArrayMap
import android.util.ArraySet

import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns

/**
 * All ringtone data is accessed via this model.
 */
internal class RingtoneModel(private val mContext: Context, private val mPrefs: SharedPreferences) {
    /** Maps ringtone uri to ringtone title; looking up a title from scratch is expensive.  */
    private val mRingtoneTitles: MutableMap<Uri, String?> = ArrayMap(16)

    /** Clears data structures containing data that is locale-sensitive.  */
    private val mLocaleChangedReceiver: BroadcastReceiver = LocaleChangedReceiver()

    /** A mutable copy of the custom ringtones.  */
    private var mCustomRingtones: MutableList<CustomRingtone>? = null

    init {
        // Clear caches affected by system settings when system settings change.
        val cr: ContentResolver = mContext.getContentResolver()
        val observer: ContentObserver = SystemAlarmAlertChangeObserver()
        cr.registerContentObserver(Settings.System.DEFAULT_ALARM_ALERT_URI, false, observer)

        // Clear caches affected by locale when locale changes.
        val localeBroadcastFilter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
        mContext.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter)
    }

    fun addCustomRingtone(uri: Uri, title: String?): CustomRingtone? {
        // If the uri is already present in an existing ringtone, do nothing.
        val existing = getCustomRingtone(uri)
        if (existing != null) {
            return existing
        }

        val ringtone = CustomRingtoneDAO.addCustomRingtone(mPrefs, uri, title)
        mutableCustomRingtones.add(ringtone)
        mutableCustomRingtones.sort()
        return ringtone
    }

    fun removeCustomRingtone(uri: Uri) {
        val ringtones = mutableCustomRingtones
        for (ringtone in ringtones) {
            if (ringtone.uri.equals(uri)) {
                CustomRingtoneDAO.removeCustomRingtone(mPrefs, ringtone.id)
                ringtones.remove(ringtone)
                break
            }
        }
    }

    private fun getCustomRingtone(uri: Uri): CustomRingtone? {
        for (ringtone in mutableCustomRingtones) {
            if (ringtone.uri.equals(uri)) {
                return ringtone
            }
        }

        return null
    }

    val customRingtones: List<CustomRingtone>
        get() = mutableCustomRingtones

    @SuppressLint("NewApi")
    fun loadRingtonePermissions() {
        val ringtones = mutableCustomRingtones
        if (ringtones.isEmpty()) {
            return
        }

        val uriPermissions: List<UriPermission> =
                mContext.getContentResolver().getPersistedUriPermissions()
        val permissions: MutableSet<Uri?> = ArraySet(uriPermissions.size)
        for (uriPermission in uriPermissions) {
            permissions.add(uriPermission.getUri())
        }

        val i = ringtones.listIterator()
        while (i.hasNext()) {
            val ringtone = i.next()
            i.set(ringtone.setHasPermissions(permissions.contains(ringtone.uri)))
        }
    }

    fun loadRingtoneTitles() {
        // Early return if the cache is already primed.
        if (mRingtoneTitles.isNotEmpty()) {
            return
        }

        val ringtoneManager = RingtoneManager(mContext)
        ringtoneManager.setType(STREAM_ALARM)

        // Cache a title for each system ringtone.
        try {
            val cursor: Cursor? = ringtoneManager.getCursor()
            cursor?.let {
                cursor.moveToFirst()
                while (!cursor.isAfterLast()) {
                    val ringtoneTitle: String = cursor.getString(TITLE_COLUMN_INDEX)
                    val ringtoneUri: Uri = ringtoneManager.getRingtoneUri(cursor.getPosition())
                    mRingtoneTitles[ringtoneUri] = ringtoneTitle
                    cursor.moveToNext()
                }
            }
        } catch (ignored: Throwable) {
            // best attempt only
            LogUtils.e("Error loading ringtone title cache", ignored)
        }
    }

    fun getRingtoneTitle(uri: Uri): String? {
        // Special case: no ringtone has a title of "Silent".
        if (AlarmSettingColumns.NO_RINGTONE_URI.equals(uri)) {
            return mContext.getString(R.string.silent_ringtone_title)
        }

        // If the ringtone is custom, it has its own title.
        val customRingtone = getCustomRingtone(uri)
        if (customRingtone != null) {
            return customRingtone.title
        }

        // Check the cache.
        var title = mRingtoneTitles[uri]

        if (title == null) {
            // This is slow because a media player is created during Ringtone object creation.
            val ringtone: Ringtone? = RingtoneManager.getRingtone(mContext, uri)
            if (ringtone == null) {
                LogUtils.e("No ringtone for uri: %s", uri)
                return mContext.getString(R.string.unknown_ringtone_title)
            }

            // Cache the title for later use.
            title = ringtone.getTitle(mContext)
            mRingtoneTitles[uri] = title
        }
        return title
    }

    private val mutableCustomRingtones: MutableList<CustomRingtone>
        get() {
            if (mCustomRingtones == null) {
                mCustomRingtones = CustomRingtoneDAO.getCustomRingtones(mPrefs)
                mCustomRingtones!!.sort()
            }

            return mCustomRingtones!!
        }

    /**
     * This receiver is notified when system settings change. Cached information built on
     * those system settings must be cleared.
     */
    private inner class SystemAlarmAlertChangeObserver
        : ContentObserver(Handler(Looper.myLooper()!!)) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)

            // Titles such as "Default ringtone (Oxygen)" are wrong after default ringtone changes.
            mRingtoneTitles.clear()
        }
    }

    /**
     * Cached information that is locale-sensitive must be cleared in response to locale changes.
     */
    private inner class LocaleChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Titles such as "Default ringtone (Oxygen)" are wrong after locale changes.
            mRingtoneTitles.clear()
        }
    }
}