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

import android.annotation.TargetApi
import android.app.NotificationManager
import android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED
import android.app.NotificationManager.INTERRUPTION_FILTER_NONE
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioManager.STREAM_ALARM
import android.media.RingtoneManager
import android.media.RingtoneManager.TYPE_ALARM
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings.System.CONTENT_URI
import android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
import androidx.core.app.NotificationManagerCompat

import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel.SilentSetting

/**
 * This model fetches and stores reasons that alarms may be suppressed or silenced by system
 * settings on the device. This information is displayed passively to notify the user of this
 * condition and set their expectations for future firing alarms.
 */
internal class SilentSettingsModel(
    private val mContext: Context,
    /** Used to determine if the application is in the foreground.  */
    private val mNotificationModel: NotificationModel
) {

    /** Used to query the alarm volume and display the system control to change the alarm volume. */
    private val mAudioManager = mContext.getSystemService(AUDIO_SERVICE) as AudioManager

    /** Used to query the do-not-disturb setting value, also called "interruption filter".  */
    private val mNotificationManager =
            mContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    /** List of listeners to invoke upon silence state change.  */
    private val mListeners: MutableList<OnSilentSettingsListener> = ArrayList(1)

    /**
     * The last setting known to be blocking alarms; `null` indicates no settings are
     * blocking the app or the app is not in the foreground.
     */
    private var mSilentSetting: SilentSetting? = null

    /** The background task that checks the device system settings that influence alarm firing.  */
    private var mCheckSilenceSettingsTask: CheckSilenceSettingsTask? = null

    init {
        // Watch for changes to the settings that may silence alarms.
        val cr: ContentResolver = mContext.getContentResolver()
        val contentChangeWatcher: ContentObserver = ContentChangeWatcher()
        cr.registerContentObserver(VOLUME_URI, false, contentChangeWatcher)
        cr.registerContentObserver(DEFAULT_ALARM_ALERT_URI, false, contentChangeWatcher)
        if (Utils.isMOrLater) {
            val filter = IntentFilter(ACTION_INTERRUPTION_FILTER_CHANGED)
            mContext.registerReceiver(DoNotDisturbChangeReceiver(), filter)
        }
    }

    fun addSilentSettingsListener(listener: OnSilentSettingsListener) {
        mListeners.add(listener)
    }

    fun removeSilentSettingsListener(listener: OnSilentSettingsListener) {
        mListeners.remove(listener)
    }

    /**
     * If the app is in the foreground, start a task to determine if any device setting will block
     * alarms from firing. If the app is in the background, clear any results from the last time
     * those settings were inspected.
     */
    fun updateSilentState() {
        // Cancel any task in flight, the result is no longer relevant.
        if (mCheckSilenceSettingsTask != null) {
            mCheckSilenceSettingsTask!!.cancel(true)
            mCheckSilenceSettingsTask = null
        }

        if (mNotificationModel.isApplicationInForeground) {
            mCheckSilenceSettingsTask = CheckSilenceSettingsTask()
            mCheckSilenceSettingsTask!!.execute()
        } else {
            setSilentState(null)
        }
    }

    /**
     * @param silentSetting the latest notion of which setting is suppressing alarms; `null`
     * if no settings are suppressing alarms
     */
    private fun setSilentState(silentSetting: SilentSetting?) {
        if (mSilentSetting != silentSetting) {
            val oldReason = mSilentSetting
            mSilentSetting = silentSetting
            for (listener in mListeners) {
                listener.onSilentSettingsChange(oldReason, silentSetting)
            }
        }
    }

    /**
     * This task inspects a variety of system settings that can prevent alarms from firing or the
     * associated ringtone from playing. If any of them would prevent an alarm from firing or
     * making noise, a description of the setting is reported to this model on the main thread.
     */
    // TODO(b/165664115) Replace deprecated AsyncTask calls
    private inner class CheckSilenceSettingsTask : AsyncTask<Void?, Void?, SilentSetting?>() {
        override fun doInBackground(vararg parameters: Void?): SilentSetting? {
            if (!isCancelled() && isDoNotDisturbBlockingAlarms) {
                return SilentSetting.DO_NOT_DISTURB
            } else if (!isCancelled() && isAlarmStreamMuted) {
                return SilentSetting.MUTED_VOLUME
            } else if (!isCancelled() && isSystemAlarmRingtoneSilent) {
                return SilentSetting.SILENT_RINGTONE
            } else if (!isCancelled() && isAppNotificationBlocked) {
                return SilentSetting.BLOCKED_NOTIFICATIONS
            }
            return null
        }

        override fun onCancelled() {
            super.onCancelled()
            if (mCheckSilenceSettingsTask == this) {
                mCheckSilenceSettingsTask = null
            }
        }

        override fun onPostExecute(silentSetting: SilentSetting?) {
            if (mCheckSilenceSettingsTask == this) {
                mCheckSilenceSettingsTask = null
                setSilentState(silentSetting)
            }
        }

        @get:TargetApi(Build.VERSION_CODES.M)
        private val isDoNotDisturbBlockingAlarms: Boolean
            get() = if (!Utils.isMOrLater) {
                false
            } else try {
                val interruptionFilter: Int = mNotificationManager.getCurrentInterruptionFilter()
                interruptionFilter == INTERRUPTION_FILTER_NONE
            } catch (e: Exception) {
                // Since this is purely informational, avoid crashing the app.
                false
            }

        private val isAlarmStreamMuted: Boolean
            get() = try {
                mAudioManager.getStreamVolume(STREAM_ALARM) <= 0
            } catch (e: Exception) {
                // Since this is purely informational, avoid crashing the app.
                false
            }

        private val isSystemAlarmRingtoneSilent: Boolean
            get() = try {
                RingtoneManager.getActualDefaultRingtoneUri(mContext, TYPE_ALARM) == null
            } catch (e: Exception) {
                // Since this is purely informational, avoid crashing the app.
                false
            }

        private val isAppNotificationBlocked: Boolean
            get() = try {
                !NotificationManagerCompat.from(mContext).areNotificationsEnabled()
            } catch (e: Exception) {
                // Since this is purely informational, avoid crashing the app.
                false
            }
    }

    /**
     * Observe changes to specific URI for settings that can silence firing alarms.
     */
    private inner class ContentChangeWatcher : ContentObserver(Handler(Looper.myLooper()!!)) {
        override fun onChange(selfChange: Boolean) {
            updateSilentState()
        }
    }

    /**
     * Observe changes to the do-not-disturb setting.
     */
    private inner class DoNotDisturbChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateSilentState()
        }
    }

    companion object {
        /** The Uri to the settings entry that stores alarm stream volume.  */
        private val VOLUME_URI: Uri = Uri.withAppendedPath(CONTENT_URI, "volume_alarm_speaker")
    }
}