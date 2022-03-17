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

package com.android.deskclock

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.telephony.TelephonyManager

import java.io.IOException
import java.lang.reflect.Method

import kotlin.math.pow

/**
 *
 * This class controls playback of ringtones. Uses [Ringtone] or [MediaPlayer] in a
 * dedicated thread so that this class can be called from the main thread. Consequently, problems
 * controlling the ringtone do not cause ANRs in the main thread of the application.
 *
 * This class also serves a second purpose. It accomplishes alarm ringtone playback using two
 * different mechanisms depending on the underlying platform.
 *
 * Prior to the M platform release, ringtone playback is accomplished using
 * [MediaPlayer]. android.permission.READ_EXTERNAL_STORAGE is required to play custom
 * ringtones located on the SD card using this mechanism. [MediaPlayer] allows clients to
 * adjust the volume of the stream and specify that the stream should be looped.
 *
 * Starting with the M platform release, ringtone playback is accomplished using
 * [Ringtone]. android.permission.READ_EXTERNAL_STORAGE is **NOT** required
 * to play custom ringtones located on the SD card using this mechanism. [Ringtone] allows
 * clients to adjust the volume of the stream and specify that the stream should be looped but
 * those methods are marked @hide in M and thus invoked using reflection. Consequently, revoking
 * the android.permission.READ_EXTERNAL_STORAGE permission has no effect on playback in M+.
 *
 * If either the [Ringtone] or [MediaPlayer] fails to play the requested audio, an
 * [in-app fallback][.getFallbackRingtoneUri] is used because playing **some**
 * sort of noise is always preferable to remaining silent.
 */
class AsyncRingtonePlayer(private val mContext: Context) {
    /** Handler running on the ringtone thread.  */
    private var mHandler: Handler? = null

    /** [MediaPlayerPlaybackDelegate] on pre M; [RingtonePlaybackDelegate] on M+  */
    private var mPlaybackDelegate: PlaybackDelegate? = null

    /** Plays the ringtone.  */
    fun play(ringtoneUri: Uri?, crescendoDuration: Long) {
        LOGGER.d("Posting play.")
        postMessage(EVENT_PLAY, ringtoneUri, crescendoDuration, 0)
    }

    /** Stops playing the ringtone.  */
    fun stop() {
        LOGGER.d("Posting stop.")
        postMessage(EVENT_STOP, null, 0, 0)
    }

    /** Schedules an adjustment of the playback volume 50ms in the future.  */
    private fun scheduleVolumeAdjustment() {
        LOGGER.v("Adjusting volume.")

        // Ensure we never have more than one volume adjustment queued.
        mHandler!!.removeMessages(EVENT_VOLUME)

        // Queue the next volume adjustment.
        postMessage(EVENT_VOLUME, null, 0, 50)
    }

    /**
     * Posts a message to the ringtone-thread handler.
     *
     * @param messageCode the message to post
     * @param ringtoneUri the ringtone in question, if any
     * @param crescendoDuration the length of time, in ms, over which to crescendo the ringtone
     * @param delayMillis the amount of time to delay sending the message, if any
     */
    private fun postMessage(
        messageCode: Int,
        ringtoneUri: Uri?,
        crescendoDuration: Long,
        delayMillis: Long
    ) {
        synchronized(this) {
            if (mHandler == null) {
                mHandler = getNewHandler()
            }

            val message = mHandler!!.obtainMessage(messageCode)
            if (ringtoneUri != null) {
                val bundle = Bundle()
                bundle.putParcelable(RINGTONE_URI_KEY, ringtoneUri)
                bundle.putLong(CRESCENDO_DURATION_KEY, crescendoDuration)
                message.data = bundle
            }

            mHandler!!.sendMessageDelayed(message, delayMillis)
        }
    }

    /**
     * Creates a new ringtone Handler running in its own thread.
     */
    @SuppressLint("HandlerLeak")
    private fun getNewHandler(): Handler {
            val thread = HandlerThread("ringtone-player")
            thread.start()

            return object : Handler(thread.looper) {
                override fun handleMessage(msg: Message) {
                    when (msg.what) {
                        EVENT_PLAY -> {
                            val data = msg.data
                            val ringtoneUri = data.getParcelable<Uri>(RINGTONE_URI_KEY)
                            val crescendoDuration = data.getLong(CRESCENDO_DURATION_KEY)
                            if (playbackDelegate.play(mContext, ringtoneUri, crescendoDuration)) {
                                scheduleVolumeAdjustment()
                            }
                        }
                        EVENT_STOP -> playbackDelegate.stop(mContext)
                        EVENT_VOLUME -> if (playbackDelegate.adjustVolume(mContext)) {
                            scheduleVolumeAdjustment()
                        }
                    }
                }
            }
        }

    /**
     * Check if the executing thread is the one dedicated to controlling the ringtone playback.
     */
    private fun checkAsyncRingtonePlayerThread() {
        if (Looper.myLooper() != mHandler!!.looper) {
            LOGGER.e("Must be on the AsyncRingtonePlayer thread!",
                    IllegalStateException())
        }
    }

    /**
     * @return the platform-specific playback delegate to use to play the ringtone
     */
    private val playbackDelegate: PlaybackDelegate
        get() {
            checkAsyncRingtonePlayerThread()
            if (mPlaybackDelegate == null) {
                mPlaybackDelegate = if (Utils.isMOrLater) {
                    // Use the newer Ringtone-based playback delegate because it does not require
                    // any permissions to read from the SD card. (M+)
                    RingtonePlaybackDelegate()
                } else {
                    // Fall back to the older MediaPlayer-based playback delegate because it is the
                    // only way to force the looping of the ringtone before M. (pre M)
                    MediaPlayerPlaybackDelegate()
                }
            }
            return mPlaybackDelegate!!
        }

    /**
     * This interface abstracts away the differences between playing ringtones via [Ringtone]
     * vs [MediaPlayer].
     */
    private interface PlaybackDelegate {
        /**
         * @return `true` iff a [volume adjustment][.adjustVolume] should be scheduled
         */
        fun play(context: Context, ringtoneUri: Uri?, crescendoDuration: Long): Boolean

        /**
         * Stop any ongoing ringtone playback.
         */
        fun stop(context: Context?)

        /**
         * @return `true` iff another volume adjustment should be scheduled
         */
        fun adjustVolume(context: Context?): Boolean
    }

    /**
     * Loops playback of a ringtone using [MediaPlayer].
     */
    private inner class MediaPlayerPlaybackDelegate : PlaybackDelegate {
        /** The audio focus manager. Only used by the ringtone thread.  */
        private var mAudioManager: AudioManager? = null

        /** Non-`null` while playing a ringtone; `null` otherwise.  */
        private var mMediaPlayer: MediaPlayer? = null

        /** The duration over which to increase the volume.  */
        private var mCrescendoDuration: Long = 0

        /** The time at which the crescendo shall cease; 0 if no crescendo is present.  */
        private var mCrescendoStopTime: Long = 0

        /**
         * Starts the actual playback of the ringtone. Executes on ringtone-thread.
         */
        override fun play(context: Context, ringtoneUri: Uri?, crescendoDuration: Long): Boolean {
            checkAsyncRingtonePlayerThread()
            mCrescendoDuration = crescendoDuration

            LOGGER.i("Play ringtone via android.media.MediaPlayer.")

            if (mAudioManager == null) {
                mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }

            val inTelephoneCall = isInTelephoneCall(context)
            var alarmNoise = if (inTelephoneCall) getInCallRingtoneUri(context) else ringtoneUri
            // Fall back to the system default alarm if the database does not have an alarm stored.
            if (alarmNoise == null) {
                alarmNoise = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                LOGGER.v("Using default alarm: $alarmNoise")
            }

            mMediaPlayer = MediaPlayer()
            mMediaPlayer!!.setOnErrorListener { _, _, _ ->
                LOGGER.e("Error occurred while playing audio. Stopping AlarmKlaxon.")
                stop(context)
                true
            }

            try {
                // If alarmNoise is a custom ringtone on the sd card the app must be granted
                // android.permission.READ_EXTERNAL_STORAGE. Pre-M this is ensured at app
                // installation time. M+, this permission can be revoked by the user any time.
                mMediaPlayer!!.setDataSource(context, alarmNoise!!)

                return startPlayback(inTelephoneCall)
            } catch (t: Throwable) {
                LOGGER.e("Using the fallback ringtone, could not play $alarmNoise", t)
                // The alarmNoise may be on the sd card which could be busy right now.
                // Use the fallback ringtone.
                try {
                    // Must reset the media player to clear the error state.
                    mMediaPlayer!!.reset()
                    mMediaPlayer!!.setDataSource(context, getFallbackRingtoneUri(context))
                    return startPlayback(inTelephoneCall)
                } catch (t2: Throwable) {
                    // At this point we just don't play anything.
                    LOGGER.e("Failed to play fallback ringtone", t2)
                }
            }

            return false
        }

        /**
         * Prepare the MediaPlayer for playback if the alarm stream is not muted, then start the
         * playback.
         *
         * @param inTelephoneCall `true` if there is currently an active telephone call
         * @return `true` if a crescendo has started and future volume adjustments are
         * required to advance the crescendo effect
         */
        @Throws(IOException::class)
        private fun startPlayback(inTelephoneCall: Boolean): Boolean {
            // Do not play alarms if stream volume is 0 (typically because ringer mode is silent).
            if (mAudioManager!!.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                return false
            }

            // Indicate the ringtone should be played via the alarm stream.
            if (Utils.isLOrLater) {
                mMediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
            }

            // Check if we are in a call. If we are, use the in-call alarm resource at a low volume
            // to not disrupt the call.
            var scheduleVolumeAdjustment = false
            if (inTelephoneCall) {
                LOGGER.v("Using the in-call alarm")
                mMediaPlayer!!.setVolume(IN_CALL_VOLUME, IN_CALL_VOLUME)
            } else if (mCrescendoDuration > 0) {
                mMediaPlayer!!.setVolume(0f, 0f)

                // Compute the time at which the crescendo will stop.
                mCrescendoStopTime = Utils.now() + mCrescendoDuration
                scheduleVolumeAdjustment = true
            }

            mMediaPlayer!!.setAudioStreamType(AudioManager.STREAM_ALARM)
            mMediaPlayer!!.isLooping = true
            mMediaPlayer!!.prepare()
            mAudioManager!!.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            mMediaPlayer!!.start()

            return scheduleVolumeAdjustment
        }

        /**
         * Stops the playback of the ringtone. Executes on the ringtone-thread.
         */
        override fun stop(context: Context?) {
            checkAsyncRingtonePlayerThread()

            LOGGER.i("Stop ringtone via android.media.MediaPlayer.")

            mCrescendoDuration = 0
            mCrescendoStopTime = 0

            // Stop audio playing
            if (mMediaPlayer != null) {
                mMediaPlayer?.stop()
                mMediaPlayer?.release()
                mMediaPlayer = null
            }

            if (mAudioManager != null) {
                mAudioManager?.abandonAudioFocus(null)
            }
        }

        /**
         * Adjusts the volume of the ringtone being played to create a crescendo effect.
         */
        override fun adjustVolume(context: Context?): Boolean {
            checkAsyncRingtonePlayerThread()

            // If media player is absent or not playing, ignore volume adjustment.
            if (mMediaPlayer == null || !mMediaPlayer!!.isPlaying) {
                mCrescendoDuration = 0
                mCrescendoStopTime = 0
                return false
            }

            // If the crescendo is complete set the volume to the maximum; we're done.
            val currentTime = Utils.now()
            if (currentTime > mCrescendoStopTime) {
                mCrescendoDuration = 0
                mCrescendoStopTime = 0
                mMediaPlayer!!.setVolume(1f, 1f)
                return false
            }

            // The current volume of the crescendo is the percentage of the crescendo completed.
            val volume = computeVolume(currentTime, mCrescendoStopTime, mCrescendoDuration)
            mMediaPlayer!!.setVolume(volume, volume)
            LOGGER.i("MediaPlayer volume set to $volume")

            // Schedule the next volume bump in the crescendo.
            return true
        }
    }

    /**
     * Loops playback of a ringtone using [Ringtone].
     */
    private inner class RingtonePlaybackDelegate : PlaybackDelegate {
        /** The audio focus manager. Only used by the ringtone thread.  */
        private var mAudioManager: AudioManager? = null

        /** The current ringtone. Only used by the ringtone thread.  */
        private var mRingtone: Ringtone? = null

        /** The method to adjust playback volume; cannot be null.  */
        private lateinit var mSetVolumeMethod: Method

        /** The method to adjust playback looping; cannot be null.  */
        private lateinit var mSetLoopingMethod: Method

        /** The duration over which to increase the volume.  */
        private var mCrescendoDuration: Long = 0

        /** The time at which the crescendo shall cease; 0 if no crescendo is present.  */
        private var mCrescendoStopTime: Long = 0

        init {
            try {
                mSetVolumeMethod = Ringtone::class.java.getDeclaredMethod("setVolume",
                        Float::class.javaPrimitiveType)
            } catch (nsme: NoSuchMethodException) {
                LOGGER.e("Unable to locate method: Ringtone.setVolume(float).", nsme)
            }
            try {
                mSetLoopingMethod = Ringtone::class.java.getDeclaredMethod("setLooping",
                        Boolean::class.javaPrimitiveType)
            } catch (nsme: NoSuchMethodException) {
                LOGGER.e("Unable to locate method: Ringtone.setLooping(boolean).", nsme)
            }
        }

        /**
         * Starts the actual playback of the ringtone. Executes on ringtone-thread.
         */
        override fun play(context: Context, ringtoneUri: Uri?, crescendoDuration: Long): Boolean {
            var ringtoneUriVariable = ringtoneUri
            checkAsyncRingtonePlayerThread()
            mCrescendoDuration = crescendoDuration

            LOGGER.i("Play ringtone via android.media.Ringtone.")

            if (mAudioManager == null) {
                mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            }

            val inTelephoneCall = isInTelephoneCall(context)
            if (inTelephoneCall) {
                ringtoneUriVariable = getInCallRingtoneUri(context)
            }

            // Attempt to fetch the specified ringtone.
            mRingtone = RingtoneManager.getRingtone(context, ringtoneUriVariable)

            if (mRingtone == null) {
                // Fall back to the system default ringtone.
                ringtoneUriVariable = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mRingtone = RingtoneManager.getRingtone(context, ringtoneUriVariable)
            }

            // Attempt to enable looping the ringtone.
            try {
                mSetLoopingMethod.invoke(mRingtone, true)
            } catch (e: Exception) {
                LOGGER.e("Unable to turn looping on for android.media.Ringtone", e)

                // Fall back to the default ringtone if looping could not be enabled.
                // (Default alarm ringtone most likely has looping tags set within the .ogg file)
                mRingtone = null
            }

            // If no ringtone exists at this point there isn't much recourse.
            if (mRingtone == null) {
                LOGGER.i("Unable to locate alarm ringtone, using internal fallback ringtone.")
                ringtoneUriVariable = getFallbackRingtoneUri(context)
                mRingtone = RingtoneManager.getRingtone(context, ringtoneUriVariable)
            }

            try {
                return startPlayback(inTelephoneCall)
            } catch (t: Throwable) {
                LOGGER.e("Using the fallback ringtone, could not play $ringtoneUriVariable", t)
                // Recover from any/all playback errors by attempting to play the fallback tone.
                mRingtone = RingtoneManager.getRingtone(context, getFallbackRingtoneUri(context))
                try {
                    return startPlayback(inTelephoneCall)
                } catch (t2: Throwable) {
                    // At this point we just don't play anything.
                    LOGGER.e("Failed to play fallback ringtone", t2)
                }
            }

            return false
        }

        /**
         * Prepare the Ringtone for playback, then start the playback.
         *
         * @param inTelephoneCall `true` if there is currently an active telephone call
         * @return `true` if a crescendo has started and future volume adjustments are
         * required to advance the crescendo effect
         */
        private fun startPlayback(inTelephoneCall: Boolean): Boolean {
            // Indicate the ringtone should be played via the alarm stream.
            if (Utils.isLOrLater) {
                mRingtone!!.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
            }

            // Attempt to adjust the ringtone volume if the user is in a telephone call.
            var scheduleVolumeAdjustment = false
            if (inTelephoneCall) {
                LOGGER.v("Using the in-call alarm")
                setRingtoneVolume(IN_CALL_VOLUME)
            } else if (mCrescendoDuration > 0) {
                setRingtoneVolume(0f)

                // Compute the time at which the crescendo will stop.
                mCrescendoStopTime = Utils.now() + mCrescendoDuration
                scheduleVolumeAdjustment = true
            }

            mAudioManager!!.requestAudioFocus(null, AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

            mRingtone!!.play()

            return scheduleVolumeAdjustment
        }

        /**
         * Sets the volume of the ringtone.
         *
         * @param volume a raw scalar in range 0.0 to 1.0, where 0.0 mutes this player, and 1.0
         * corresponds to no attenuation being applied.
         */
        private fun setRingtoneVolume(volume: Float) {
            try {
                mSetVolumeMethod.invoke(mRingtone, volume)
            } catch (e: Exception) {
                LOGGER.e("Unable to set volume for android.media.Ringtone", e)
            }
        }

        /**
         * Stops the playback of the ringtone. Executes on the ringtone-thread.
         */
        override fun stop(context: Context?) {
            checkAsyncRingtonePlayerThread()

            LOGGER.i("Stop ringtone via android.media.Ringtone.")

            mCrescendoDuration = 0
            mCrescendoStopTime = 0

            if (mRingtone != null && mRingtone!!.isPlaying) {
                LOGGER.d("Ringtone.stop() invoked.")
                mRingtone!!.stop()
            }

            mRingtone = null

            if (mAudioManager != null) {
                mAudioManager!!.abandonAudioFocus(null)
            }
        }

        /**
         * Adjusts the volume of the ringtone being played to create a crescendo effect.
         */
        override fun adjustVolume(context: Context?): Boolean {
            checkAsyncRingtonePlayerThread()

            // If ringtone is absent or not playing, ignore volume adjustment.
            if (mRingtone == null || !mRingtone!!.isPlaying) {
                mCrescendoDuration = 0
                mCrescendoStopTime = 0
                return false
            }

            // If the crescendo is complete set the volume to the maximum; we're done.
            val currentTime = Utils.now()
            if (currentTime > mCrescendoStopTime) {
                mCrescendoDuration = 0
                mCrescendoStopTime = 0
                setRingtoneVolume(1f)
                return false
            }

            val volume = computeVolume(currentTime, mCrescendoStopTime, mCrescendoDuration)
            setRingtoneVolume(volume)

            // Schedule the next volume bump in the crescendo.
            return true
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("AsyncRingtonePlayer")

        // Volume suggested by media team for in-call alarms.
        private const val IN_CALL_VOLUME = 0.125f

        // Message codes used with the ringtone thread.
        private const val EVENT_PLAY = 1
        private const val EVENT_STOP = 2
        private const val EVENT_VOLUME = 3
        private const val RINGTONE_URI_KEY = "RINGTONE_URI_KEY"
        private const val CRESCENDO_DURATION_KEY = "CRESCENDO_DURATION_KEY"

        /**
         * @return `true` iff the device is currently in a telephone call
         */
        private fun isInTelephoneCall(context: Context): Boolean {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return tm.callState != TelephonyManager.CALL_STATE_IDLE
        }

        /**
         * @return Uri of the ringtone to play when the user is in a telephone call
         */
        private fun getInCallRingtoneUri(context: Context): Uri {
            return Utils.getResourceUri(context, R.raw.alarm_expire)
        }

        /**
         * @return Uri of the ringtone to play when the chosen ringtone fails to play
         */
        private fun getFallbackRingtoneUri(context: Context): Uri {
            return Utils.getResourceUri(context, R.raw.alarm_expire)
        }

        /**
         * @param currentTime current time of the device
         * @param stopTime time at which the crescendo finishes
         * @param duration length of time over which the crescendo occurs
         * @return the scalar volume value that produces a linear increase in volume (in decibels)
         */
        private fun computeVolume(currentTime: Long, stopTime: Long, duration: Long): Float {
            // Compute the percentage of the crescendo that has completed.
            val elapsedCrescendoTime = stopTime - currentTime.toFloat()
            val fractionComplete = 1 - elapsedCrescendoTime / duration

            // Use the fraction to compute a target decibel between
            // -40dB (near silent) and 0dB (max).
            val gain = fractionComplete * 40 - 40

            // Convert the target gain (in decibels) into the corresponding volume scalar.
            val volume = 10.0.pow(gain / 20f.toDouble()).toFloat()

            LOGGER.v("Ringtone crescendo %,.2f%% complete (scalar: %f, volume: %f dB)",
                    fractionComplete * 100, volume, gain)

            return volume
        }
    }
}