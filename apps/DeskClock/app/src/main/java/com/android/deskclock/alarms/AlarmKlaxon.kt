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

package com.android.deskclock.alarms

import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Vibrator

import com.android.deskclock.AsyncRingtonePlayer
import com.android.deskclock.LogUtils
import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.AlarmSettingColumns

/**
 * Manages playing alarm ringtones and vibrating the device.
 */
internal object AlarmKlaxon {

    private val VIBRATE_PATTERN = longArrayOf(500, 500)

    private var sStarted = false
    private var sAsyncRingtonePlayer: AsyncRingtonePlayer? = null

    @JvmStatic
    fun stop(context: Context) {
        if (sStarted) {
            LogUtils.v("AlarmKlaxon.stop()")
            sStarted = false
            getAsyncRingtonePlayer(context)!!.stop()
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
    }

    @JvmStatic
    fun start(context: Context, instance: AlarmInstance) {
        // Make sure we are stopped before starting
        stop(context)
        LogUtils.v("AlarmKlaxon.start()")

        if (!AlarmSettingColumns.NO_RINGTONE_URI.equals(instance.mRingtone)) {
            val crescendoDuration = DataModel.dataModel.alarmCrescendoDuration
            getAsyncRingtonePlayer(context)!!.play(instance.mRingtone, crescendoDuration)
        }

        if (instance.mVibrate) {
            val vibrator: Vibrator = getVibrator(context)
            if (Utils.isLOrLater) {
                vibrateLOrLater(vibrator)
            } else {
                vibrator.vibrate(VIBRATE_PATTERN, 0)
            }
        }

        sStarted = true
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun vibrateLOrLater(vibrator: Vibrator) {
        vibrator.vibrate(VIBRATE_PATTERN, 0, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
    }

    private fun getVibrator(context: Context): Vibrator {
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @Synchronized
    private fun getAsyncRingtonePlayer(context: Context): AsyncRingtonePlayer? {
        if (sAsyncRingtonePlayer == null) {
            sAsyncRingtonePlayer = AsyncRingtonePlayer(context.getApplicationContext())
        }

        return sAsyncRingtonePlayer
    }
}