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

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

import com.android.deskclock.AlarmAlertWakeLock
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.events.Events
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.InstancesColumns

/**
 * This service is in charge of starting/stopping the alarm. It will bring up and manage the
 * [AlarmActivity] as well as [AlarmKlaxon].
 *
 * Registers a broadcast receiver to listen for snooze/dismiss intents. The broadcast receiver
 * exits early if AlarmActivity is bound to prevent double-processing of the snooze/dismiss intents.
 */
class AlarmService : Service() {
    /** Binder given to AlarmActivity.  */
    private val mBinder: IBinder = Binder()

    /** Whether the service is currently bound to AlarmActivity  */
    private var mIsBound = false

    /** Listener for changes in phone state.  */
    private val mPhoneStateListener = PhoneStateChangeListener()

    /** Whether the receiver is currently registered  */
    private var mIsRegistered = false

    override fun onBind(intent: Intent?): IBinder {
        mIsBound = true
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mIsBound = false
        return super.onUnbind(intent)
    }

    private lateinit var mTelephonyManager: TelephonyManager
    private var mCurrentAlarm: AlarmInstance? = null

    private fun startAlarm(instance: AlarmInstance) {
        LogUtils.v("AlarmService.start with instance: " + instance.mId)
        if (mCurrentAlarm != null) {
            AlarmStateManager.setMissedState(this, mCurrentAlarm!!)
            stopCurrentAlarm()
        }

        AlarmAlertWakeLock.acquireCpuWakeLock(this)

        mCurrentAlarm = instance
        AlarmNotifications.showAlarmNotification(this, mCurrentAlarm!!)
        mTelephonyManager.listen(mPhoneStateListener.init(), PhoneStateListener.LISTEN_CALL_STATE)
        AlarmKlaxon.start(this, mCurrentAlarm!!)
        sendBroadcast(Intent(ALARM_ALERT_ACTION))
    }

    private fun stopCurrentAlarm() {
        if (mCurrentAlarm == null) {
            LogUtils.v("There is no current alarm to stop")
            return
        }

        val instanceId = mCurrentAlarm!!.mId
        LogUtils.v("AlarmService.stop with instance: %s", instanceId)

        AlarmKlaxon.stop(this)
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        sendBroadcast(Intent(ALARM_DONE_ACTION))

        stopForeground(true /* removeNotification */)

        mCurrentAlarm = null
        AlarmAlertWakeLock.releaseCpuLock()
    }

    private val mActionsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.getAction()
            LogUtils.i("AlarmService received intent %s", action)
            if (mCurrentAlarm == null ||
                    mCurrentAlarm!!.mAlarmState != InstancesColumns.FIRED_STATE) {
                LogUtils.i("No valid firing alarm")
                return
            }

            if (mIsBound) {
                LogUtils.i("AlarmActivity bound; AlarmService no-op")
                return
            }

            when (action) {
                ALARM_SNOOZE_ACTION -> {
                    // Set the alarm state to snoozed.
                    // If this broadcast receiver is handling the snooze intent then AlarmActivity
                    // must not be showing, so always show snooze toast.
                    AlarmStateManager.setSnoozeState(context, mCurrentAlarm!!, true /* showToast */)
                    Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent)
                }
                ALARM_DISMISS_ACTION -> {
                    // Set the alarm state to dismissed.
                    AlarmStateManager.deleteInstanceAndUpdateParent(context, mCurrentAlarm!!)
                    Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mTelephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Register the broadcast receiver
        val filter = IntentFilter(ALARM_SNOOZE_ACTION)
        filter.addAction(ALARM_DISMISS_ACTION)
        registerReceiver(mActionsReceiver, filter)
        mIsRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.v("AlarmService.onStartCommand() with %s", intent)
        if (intent == null) {
            return Service.START_NOT_STICKY
        }

        val instanceId = AlarmInstance.getId(intent.getData()!!)
        when (intent.getAction()) {
            AlarmStateManager.CHANGE_STATE_ACTION -> {
                AlarmStateManager.handleIntent(this, intent)

                // If state is changed to firing, actually fire the alarm!
                val alarmState: Int = intent.getIntExtra(AlarmStateManager.ALARM_STATE_EXTRA, -1)
                if (alarmState == InstancesColumns.FIRED_STATE) {
                    val cr: ContentResolver = this.getContentResolver()
                    val instance: AlarmInstance? = AlarmInstance.getInstance(cr, instanceId)
                    if (instance == null) {
                        LogUtils.e("No instance found to start alarm: %d", instanceId)
                        if (mCurrentAlarm != null) {
                            // Only release lock if we are not firing alarm
                            AlarmAlertWakeLock.releaseCpuLock()
                        }
                    } else if (mCurrentAlarm != null && mCurrentAlarm!!.mId == instanceId) {
                        LogUtils.e("Alarm already started for instance: %d", instanceId)
                    } else {
                        startAlarm(instance)
                    }
                }
            }
            STOP_ALARM_ACTION -> {
                if (mCurrentAlarm != null && mCurrentAlarm!!.mId != instanceId) {
                    LogUtils.e("Can't stop alarm for instance: %d because current alarm is: %d",
                            instanceId, mCurrentAlarm!!.mId)
                } else {
                    stopCurrentAlarm()
                    stopSelf()
                }
            }
        }

        return Service.START_NOT_STICKY
    }

    override fun onDestroy() {
        LogUtils.v("AlarmService.onDestroy() called")
        super.onDestroy()
        if (mCurrentAlarm != null) {
            stopCurrentAlarm()
        }

        if (mIsRegistered) {
            unregisterReceiver(mActionsReceiver)
            mIsRegistered = false
        }
    }

    private inner class PhoneStateChangeListener : PhoneStateListener() {
        private var mPhoneCallState = 0

        fun init(): PhoneStateChangeListener {
            mPhoneCallState = -1
            return this
        }

        override fun onCallStateChanged(state: Int, ignored: String?) {
            if (mPhoneCallState == -1) {
                mPhoneCallState = state
            }

            if (state != TelephonyManager.CALL_STATE_IDLE && state != mPhoneCallState) {
                startService(AlarmStateManager.createStateChangeIntent(this@AlarmService,
                        "AlarmService", mCurrentAlarm!!, InstancesColumns.MISSED_STATE))
            }
        }
    }

    companion object {
        /**
         * AlarmActivity and AlarmService (when unbound) listen for this broadcast intent
         * so that other applications can snooze the alarm (after ALARM_ALERT_ACTION and before
         * ALARM_DONE_ACTION).
         */
        const val ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE"

        /**
         * AlarmActivity and AlarmService listen for this broadcast intent so that other
         * applications can dismiss the alarm (after ALARM_ALERT_ACTION and before ALARM_DONE_ACTION).
         */
        const val ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS"

        /** A public action sent by AlarmService when the alarm has started.  */
        const val ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT"

        /** A public action sent by AlarmService when the alarm has stopped for any reason.  */
        const val ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE"

        /** Private action used to stop an alarm with this service.  */
        const val STOP_ALARM_ACTION = "STOP_ALARM"

        /**
         * Utility method to help stop an alarm properly. Nothing will happen, if alarm is not firing
         * or using a different instance.
         *
         * @param context application context
         * @param instance you are trying to stop
         */
        @JvmStatic
        fun stopAlarm(context: Context, instance: AlarmInstance) {
            val intent: Intent =
                    AlarmInstance.createIntent(context, AlarmService::class.java, instance.mId)
                            .setAction(STOP_ALARM_ACTION)

            // We don't need a wake lock here, since we are trying to kill an alarm
            context.startService(intent)
        }
    }
}