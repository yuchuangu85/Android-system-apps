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

package com.android.deskclock.controller

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.UserManager
import android.provider.AlarmClock
import androidx.annotation.StringRes

import com.android.deskclock.DeskClock
import com.android.deskclock.HandleApiCalls
import com.android.deskclock.HandleShortcuts
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.ScreensaverActivity
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.Lap
import com.android.deskclock.data.Stopwatch
import com.android.deskclock.data.StopwatchListener
import com.android.deskclock.events.Events
import com.android.deskclock.events.ShortcutEventTracker
import com.android.deskclock.stopwatch.StopwatchService
import com.android.deskclock.uidata.UiDataModel

@TargetApi(Build.VERSION_CODES.N_MR1)
internal class ShortcutController(val context: Context) {
    private val mComponentName = ComponentName(context, DeskClock::class.java)
    private val mShortcutManager = context.getSystemService(ShortcutManager::class.java)
    private val mUserManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    init {
        Controller.getController().addEventTracker(ShortcutEventTracker(context))
        DataModel.dataModel.addStopwatchListener(StopwatchWatcher())
    }

    fun updateShortcuts() {
        if (!mUserManager.isUserUnlocked()) {
            LogUtils.i("Skipping shortcut update because user is locked.")
            return
        }
        try {
            val alarm: ShortcutInfo = createNewAlarmShortcut()
            val timer: ShortcutInfo = createNewTimerShortcut()
            val stopwatch: ShortcutInfo = createStopwatchShortcut()
            val screensaver: ShortcutInfo = createScreensaverShortcut()
            mShortcutManager.setDynamicShortcuts(listOf(alarm, timer, stopwatch, screensaver))
        } catch (e: IllegalStateException) {
            LogUtils.wtf(e)
        }
    }

    private fun createNewAlarmShortcut(): ShortcutInfo {
        val intent: Intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .setClass(context, HandleApiCalls::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val setAlarmShortcut = UiDataModel.uiDataModel
                .getShortcutId(R.string.category_alarm, R.string.action_create)
        return ShortcutInfo.Builder(context, setAlarmShortcut)
                .setIcon(Icon.createWithResource(context, R.drawable.shortcut_new_alarm))
                .setActivity(mComponentName)
                .setShortLabel(context.getString(R.string.shortcut_new_alarm_short))
                .setLongLabel(context.getString(R.string.shortcut_new_alarm_long))
                .setIntent(intent)
                .setRank(0)
                .build()
    }

    private fun createNewTimerShortcut(): ShortcutInfo {
        val intent: Intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .setClass(context, HandleApiCalls::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val setTimerShortcut = UiDataModel.uiDataModel
                .getShortcutId(R.string.category_timer, R.string.action_create)
        return ShortcutInfo.Builder(context, setTimerShortcut)
                .setIcon(Icon.createWithResource(context, R.drawable.shortcut_new_timer))
                .setActivity(mComponentName)
                .setShortLabel(context.getString(R.string.shortcut_new_timer_short))
                .setLongLabel(context.getString(R.string.shortcut_new_timer_long))
                .setIntent(intent)
                .setRank(1)
                .build()
    }

    private fun createStopwatchShortcut(): ShortcutInfo {
        @StringRes val action: Int = if (DataModel.dataModel.stopwatch.isRunning) {
            R.string.action_pause
        } else {
            R.string.action_start
        }
        val shortcutId = UiDataModel.uiDataModel
                .getShortcutId(R.string.category_stopwatch, action)
        val shortcut: ShortcutInfo.Builder = ShortcutInfo.Builder(context, shortcutId)
                .setIcon(Icon.createWithResource(context, R.drawable.shortcut_stopwatch))
                .setActivity(mComponentName)
                .setRank(2)
        val intent: Intent
        if (DataModel.dataModel.stopwatch.isRunning) {
            intent = Intent(StopwatchService.ACTION_PAUSE_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
            shortcut.setShortLabel(context.getString(R.string.shortcut_pause_stopwatch_short))
                    .setLongLabel(context.getString(R.string.shortcut_pause_stopwatch_long))
        } else {
            intent = Intent(StopwatchService.ACTION_START_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
            shortcut.setShortLabel(context.getString(R.string.shortcut_start_stopwatch_short))
                    .setLongLabel(context.getString(R.string.shortcut_start_stopwatch_long))
        }
        intent.setClass(context, HandleShortcuts::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return shortcut
                .setIntent(intent)
                .build()
    }

    private fun createScreensaverShortcut(): ShortcutInfo {
        val intent: Intent = Intent(Intent.ACTION_MAIN)
                .setClass(context, ScreensaverActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val screensaverShortcut = UiDataModel.uiDataModel
                .getShortcutId(R.string.category_screensaver, R.string.action_show)
        return ShortcutInfo.Builder(context, screensaverShortcut)
                .setIcon(Icon.createWithResource(context, R.drawable.shortcut_screensaver))
                .setActivity(mComponentName)
                .setShortLabel(context.getString(R.string.shortcut_start_screensaver_short))
                .setLongLabel(context.getString(R.string.shortcut_start_screensaver_long))
                .setIntent(intent)
                .setRank(3)
                .build()
    }

    private inner class StopwatchWatcher : StopwatchListener {

        override fun stopwatchUpdated(before: Stopwatch, after: Stopwatch) {
            if (!mUserManager.isUserUnlocked()) {
                LogUtils.i("Skipping stopwatch shortcut update because user is locked.")
                return
            }
            try {
                mShortcutManager.updateShortcuts(listOf(createStopwatchShortcut()))
            } catch (e: IllegalStateException) {
                LogUtils.wtf(e)
            }
        }

        override fun lapAdded(lap: Lap) {}
    }
}