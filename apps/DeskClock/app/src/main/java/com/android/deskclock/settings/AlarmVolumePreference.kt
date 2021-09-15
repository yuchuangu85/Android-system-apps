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

import android.annotation.TargetApi
import android.app.NotificationManager
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.Context.NOTIFICATION_SERVICE
import android.database.ContentObserver
import android.media.AudioManager
import android.media.AudioManager.STREAM_ALARM
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

import com.android.deskclock.R
import com.android.deskclock.RingtonePreviewKlaxon
import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel

class AlarmVolumePreference(context: Context?, attrs: AttributeSet?) : Preference(context, attrs) {
    private lateinit var mSeekbar: SeekBar
    private lateinit var mAlarmIcon: ImageView

    private var mPreviewPlaying = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val context: Context = getContext()
        val audioManager: AudioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager

        // Disable click feedback for this preference.
        holder.itemView.setClickable(false)
        mSeekbar = holder.findViewById(R.id.alarm_volume_slider) as SeekBar
        mSeekbar.setMax(audioManager.getStreamMaxVolume(STREAM_ALARM))
        mSeekbar.setProgress(audioManager.getStreamVolume(STREAM_ALARM))
        mAlarmIcon = holder.findViewById(R.id.alarm_icon) as ImageView
        onSeekbarChanged()

        val volumeObserver: ContentObserver = object : ContentObserver(mSeekbar.getHandler()) {
            override fun onChange(selfChange: Boolean) {
                // Volume was changed elsewhere, update our slider.
                mSeekbar.setProgress(audioManager.getStreamVolume(STREAM_ALARM))
            }
        }

        mSeekbar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View?) {
                context.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI,
                        true, volumeObserver)
            }

            override fun onViewDetachedFromWindow(v: View?) {
                context.getContentResolver().unregisterContentObserver(volumeObserver)
            }
        })

        mSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(STREAM_ALARM, progress, 0)
                }
                onSeekbarChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (!mPreviewPlaying && seekBar.getProgress() != 0) {
                    // If we are not currently playing and progress is set to non-zero, start.
                    RingtonePreviewKlaxon
                            .start(context, DataModel.dataModel.defaultAlarmRingtoneUri)
                    mPreviewPlaying = true
                    seekBar.postDelayed(Runnable {
                        RingtonePreviewKlaxon.stop(context)
                        mPreviewPlaying = false
                    }, ALARM_PREVIEW_DURATION_MS)
                }
            }
        })
    }

    private fun onSeekbarChanged() {
        mSeekbar.setEnabled(doesDoNotDisturbAllowAlarmPlayback())
        val imageRes = if (mSeekbar.getProgress() == 0) {
            R.drawable.ic_alarm_off_24dp
        } else {
            R.drawable.ic_alarm_small
        }
        mAlarmIcon.setImageResource(imageRes)
    }

    private fun doesDoNotDisturbAllowAlarmPlayback(): Boolean {
        return !Utils.isNOrLater || doesDoNotDisturbAllowAlarmPlaybackNPlus()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun doesDoNotDisturbAllowAlarmPlaybackNPlus(): Boolean {
        val notificationManager =
                getContext().getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.getCurrentInterruptionFilter() !=
                NotificationManager.INTERRUPTION_FILTER_NONE
    }

    companion object {
        private const val ALARM_PREVIEW_DURATION_MS: Long = 2000
    }
}