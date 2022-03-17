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
package com.android.alarmclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

import com.android.deskclock.DeskClock
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.data.DataModel

/**
 * Simple widget to show an analog clock.
 */
class AnalogAppWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        val wm: AppWidgetManager = AppWidgetManager.getInstance(context) ?: return

        // Send events for newly created/deleted widgets.
        val provider = ComponentName(context, javaClass)
        val widgetCount: Int = wm.getAppWidgetIds(provider).size
        val dm = DataModel.dataModel
        dm.updateWidgetCount(javaClass, widgetCount, R.string.category_analog_widget)
    }

    /**
     * Called when widgets must provide remote views.
     */
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        super.onUpdate(context, wm, widgetIds)
        widgetIds.forEach { widgetId ->
            val packageName: String = context.getPackageName()
            val widget = RemoteViews(packageName, R.layout.analog_appwidget)

            // Tapping on the widget opens the app (if not on the lock screen).
            if (Utils.isWidgetClickable(wm, widgetId)) {
                val openApp = Intent(context, DeskClock::class.java)
                val pi: PendingIntent = PendingIntent.getActivity(context, 0, openApp, 0)
                widget.setOnClickPendingIntent(R.id.analog_appwidget, pi)
            }
            wm.updateAppWidget(widgetId, widget)
        }
    }
}