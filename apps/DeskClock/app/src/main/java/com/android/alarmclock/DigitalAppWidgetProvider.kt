/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.alarmclock

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_NO_CREATE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_LOCALE_CHANGED
import android.content.Intent.ACTION_SCREEN_ON
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.ArraySet
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.View.VISIBLE
import android.widget.RemoteViews
import android.widget.TextClock
import android.widget.TextView

import com.android.deskclock.DeskClock
import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.alarms.AlarmStateManager
import com.android.deskclock.data.DataModel
import com.android.deskclock.uidata.UiDataModel
import com.android.deskclock.worldclock.CitySelectionActivity

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * This provider produces a widget resembling one of the formats below.
 *
 * If an alarm is scheduled to ring in the future:
 * <pre>
 *      12:59 AM
 *      WED, FEB 3 ⏰ THU 9:30 AM
 * </pre>
 *
 * If no alarm is scheduled to ring in the future:
 * <pre>
 *      12:59 AM
 *      WED, FEB 3
 * </pre>
 *
 * This widget is scaling the font sizes to fit within the widget bounds chosen by the user without
 * any clipping. To do so it measures layouts offscreen using a range of font sizes in order to
 * choose optimal values.
 */
class DigitalAppWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)

        // Schedule the day-change callback if necessary.
        updateDayChangeCallback(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        // Remove any scheduled day-change callback.
        removeDayChangeCallback(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        LOGGER.i("onReceive: $intent")
        super.onReceive(context, intent)

        val wm: AppWidgetManager = AppWidgetManager.getInstance(context) ?: return

        val provider = ComponentName(context, javaClass)
        val widgetIds: IntArray = wm.getAppWidgetIds(provider)

        val action: String? = intent.action
        when (action) {
            ACTION_NEXT_ALARM_CLOCK_CHANGED,
            ACTION_DATE_CHANGED,
            ACTION_LOCALE_CHANGED,
            ACTION_SCREEN_ON,
            ACTION_TIME_CHANGED,
            ACTION_TIMEZONE_CHANGED,
            AlarmStateManager.ACTION_ALARM_CHANGED,
            ACTION_ON_DAY_CHANGE,
            DataModel.ACTION_WORLD_CITIES_CHANGED -> widgetIds.forEach { widgetId ->
                relayoutWidget(context, wm, widgetId, wm.getAppWidgetOptions(widgetId))
            }
        }

        val dm = DataModel.dataModel
        dm.updateWidgetCount(javaClass, widgetIds.size, R.string.category_digital_widget)

        if (widgetIds.size > 0) {
            updateDayChangeCallback(context)
        }
    }

    /**
     * Called when widgets must provide remote views.
     */
    override fun onUpdate(context: Context, wm: AppWidgetManager, widgetIds: IntArray) {
        super.onUpdate(context, wm, widgetIds)

        widgetIds.forEach { widgetId ->
            relayoutWidget(context, wm, widgetId, wm.getAppWidgetOptions(widgetId))
        }
    }

    /**
     * Called when the app widget changes sizes.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        wm: AppWidgetManager?,
        widgetId: Int,
        options: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, wm, widgetId, options)

        // Scale the fonts of the clock to fit inside the new size
        relayoutWidget(context, AppWidgetManager.getInstance(context), widgetId, options)
    }

    /**
     * Remove the existing day-change callback if it is not needed (no selected cities exist).
     * Add the day-change callback if it is needed (selected cities exist).
     */
    private fun updateDayChangeCallback(context: Context) {
        val dm = DataModel.dataModel
        val selectedCities = dm.selectedCities
        val showHomeClock = dm.showHomeClock
        if (selectedCities.isEmpty() && !showHomeClock) {
            // Remove the existing day-change callback.
            removeDayChangeCallback(context)
            return
        }

        // Look up the time at which the next day change occurs across all timezones.
        val zones: MutableSet<TimeZone> = ArraySet(selectedCities.size + 2)
        zones.add(TimeZone.getDefault())
        if (showHomeClock) {
            zones.add(dm.homeCity.timeZone)
        }
        selectedCities.forEach { city ->
            zones.add(city.timeZone)
        }
        val nextDay = Utils.getNextDay(Date(), zones)

        // Schedule the next day-change callback; at least one city is displayed.
        val pi: PendingIntent =
                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT, FLAG_UPDATE_CURRENT)
        getAlarmManager(context).setExact(AlarmManager.RTC, nextDay.time, pi)
    }

    /**
     * Remove the existing day-change callback.
     */
    private fun removeDayChangeCallback(context: Context) {
        val pi: PendingIntent? =
                PendingIntent.getBroadcast(context, 0, DAY_CHANGE_INTENT, FLAG_NO_CREATE)
        if (pi != null) {
            getAlarmManager(context).cancel(pi)
            pi.cancel()
        }
    }

    /**
     * This class stores the target size of the widget as well as the measured size using a given
     * clock font size. All other fonts and icons are scaled proportional to the clock font.
     */
    private class Sizes(
        val mTargetWidthPx: Int,
        val mTargetHeightPx: Int,
        val largestClockFontSizePx: Int
    ) {
        val smallestClockFontSizePx = 1
        var mIconBitmap: Bitmap? = null

        var mMeasuredWidthPx = 0
        var mMeasuredHeightPx = 0
        var mMeasuredTextClockWidthPx = 0
        var mMeasuredTextClockHeightPx = 0

        /** The size of the font to use on the date / next alarm time fields.  */
        var mFontSizePx = 0

        /** The size of the font to use on the clock field.  */
        var mClockFontSizePx = 0

        var mIconFontSizePx = 0
        var mIconPaddingPx = 0

        var clockFontSizePx: Int
            get() = mClockFontSizePx
            set(clockFontSizePx) {
                mClockFontSizePx = clockFontSizePx
                mFontSizePx = Math.max(1, Math.round(clockFontSizePx / 7.5f))
                mIconFontSizePx = (mFontSizePx * 1.4f).toInt()
                mIconPaddingPx = mFontSizePx / 3
            }

        /**
         * @return the amount of widget height available to the world cities list
         */
        val listHeight: Int
            get() = mTargetHeightPx - mMeasuredHeightPx

        fun hasViolations(): Boolean {
            return mMeasuredWidthPx > mTargetWidthPx || mMeasuredHeightPx > mTargetHeightPx
        }

        fun newSize(): Sizes {
            return Sizes(mTargetWidthPx, mTargetHeightPx, largestClockFontSizePx)
        }

        override fun toString(): String {
            val builder = StringBuilder(1000)
            builder.append("\n")
            append(builder, "Target dimensions: %dpx x %dpx\n", mTargetWidthPx, mTargetHeightPx)
            append(builder, "Last valid widget container measurement: %dpx x %dpx\n",
                    mMeasuredWidthPx, mMeasuredHeightPx)
            append(builder, "Last text clock measurement: %dpx x %dpx\n",
                    mMeasuredTextClockWidthPx, mMeasuredTextClockHeightPx)
            if (mMeasuredWidthPx > mTargetWidthPx) {
                append(builder, "Measured width %dpx exceeded widget width %dpx\n",
                        mMeasuredWidthPx, mTargetWidthPx)
            }
            if (mMeasuredHeightPx > mTargetHeightPx) {
                append(builder, "Measured height %dpx exceeded widget height %dpx\n",
                        mMeasuredHeightPx, mTargetHeightPx)
            }
            append(builder, "Clock font: %dpx\n", mClockFontSizePx)
            return builder.toString()
        }

        companion object {
            private fun append(builder: StringBuilder, format: String, vararg args: Any) {
                builder.append(String.format(Locale.ENGLISH, format, *args))
            }
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("DigitalWidgetProvider")

        /**
         * Intent action used for refreshing a world city display when any of them changes days or when
         * the default TimeZone changes days. This affects the widget display because the day-of-week is
         * only visible when the world city day-of-week differs from the default TimeZone's day-of-week.
         */
        private const val ACTION_ON_DAY_CHANGE = "com.android.deskclock.ON_DAY_CHANGE"

        /** Intent used to deliver the [.ACTION_ON_DAY_CHANGE] callback.  */
        private val DAY_CHANGE_INTENT: Intent = Intent(ACTION_ON_DAY_CHANGE)

        /**
         * Compute optimal font and icon sizes offscreen for both portrait and landscape orientations
         * using the last known widget size and apply them to the widget.
         */
        private fun relayoutWidget(
            context: Context,
            wm: AppWidgetManager,
            widgetId: Int,
            options: Bundle
        ) {
            val portrait: RemoteViews = relayoutWidget(context, wm, widgetId, options, true)
            val landscape: RemoteViews = relayoutWidget(context, wm, widgetId, options, false)
            val widget = RemoteViews(landscape, portrait)
            wm.updateAppWidget(widgetId, widget)
            wm.notifyAppWidgetViewDataChanged(widgetId, R.id.world_city_list)
        }

        /**
         * Compute optimal font and icon sizes offscreen for the given orientation.
         */
        private fun relayoutWidget(
            context: Context,
            wm: AppWidgetManager,
            widgetId: Int,
            options: Bundle?,
            portrait: Boolean
        ): RemoteViews {
            // Create a remote view for the digital clock.
            val packageName: String = context.getPackageName()
            val rv = RemoteViews(packageName, R.layout.digital_widget)

            // Tapping on the widget opens the app (if not on the lock screen).
            if (Utils.isWidgetClickable(wm, widgetId)) {
                val openApp = Intent(context, DeskClock::class.java)
                val pi: PendingIntent = PendingIntent.getActivity(context, 0, openApp, 0)
                rv.setOnClickPendingIntent(R.id.digital_widget, pi)
            }

            // Configure child views of the remote view.
            val dateFormat: CharSequence = getDateFormat(context)
            rv.setCharSequence(R.id.date, "setFormat12Hour", dateFormat)
            rv.setCharSequence(R.id.date, "setFormat24Hour", dateFormat)

            val nextAlarmTime: String? = Utils.getNextAlarm(context)
            if (TextUtils.isEmpty(nextAlarmTime)) {
                rv.setViewVisibility(R.id.nextAlarm, GONE)
                rv.setViewVisibility(R.id.nextAlarmIcon, GONE)
            } else {
                rv.setTextViewText(R.id.nextAlarm, nextAlarmTime)
                rv.setViewVisibility(R.id.nextAlarm, VISIBLE)
                rv.setViewVisibility(R.id.nextAlarmIcon, VISIBLE)
            }

            val options = options ?: wm.getAppWidgetOptions(widgetId)

            // Fetch the widget size selected by the user.
            val resources: Resources = context.getResources()
            val density: Float = resources.getDisplayMetrics().density
            val minWidthPx = (density * options.getInt(OPTION_APPWIDGET_MIN_WIDTH)).toInt()
            val minHeightPx = (density * options.getInt(OPTION_APPWIDGET_MIN_HEIGHT)).toInt()
            val maxWidthPx = (density * options.getInt(OPTION_APPWIDGET_MAX_WIDTH)).toInt()
            val maxHeightPx = (density * options.getInt(OPTION_APPWIDGET_MAX_HEIGHT)).toInt()
            val targetWidthPx = if (portrait) minWidthPx else maxWidthPx
            val targetHeightPx = if (portrait) maxHeightPx else minHeightPx
            val largestClockFontSizePx: Int =
                    resources.getDimensionPixelSize(R.dimen.widget_max_clock_font_size)

            // Create a size template that describes the widget bounds.
            val template = Sizes(targetWidthPx, targetHeightPx, largestClockFontSizePx)

            // Compute optimal font sizes and icon sizes to fit within the widget bounds.
            val sizes = optimizeSizes(context, template, nextAlarmTime)
            if (LOGGER.isVerboseLoggable) {
                LOGGER.v(sizes.toString())
            }

            // Apply the computed sizes to the remote views.
            rv.setImageViewBitmap(R.id.nextAlarmIcon, sizes.mIconBitmap)
            rv.setTextViewTextSize(R.id.date, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())
            rv.setTextViewTextSize(R.id.nextAlarm, COMPLEX_UNIT_PX, sizes.mFontSizePx.toFloat())
            rv.setTextViewTextSize(R.id.clock, COMPLEX_UNIT_PX, sizes.mClockFontSizePx.toFloat())

            val smallestWorldCityListSizePx: Int =
                    resources.getDimensionPixelSize(R.dimen.widget_min_world_city_list_size)
            if (sizes.listHeight <= smallestWorldCityListSizePx) {
                // Insufficient space; hide the world city list.
                rv.setViewVisibility(R.id.world_city_list, GONE)
            } else {
                // Set an adapter on the world city list. That adapter connects to a Service via intent.
                val intent = Intent(context, DigitalAppWidgetCityService::class.java)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                rv.setRemoteAdapter(R.id.world_city_list, intent)
                rv.setViewVisibility(R.id.world_city_list, VISIBLE)

                // Tapping on the widget opens the city selection activity (if not on the lock screen).
                if (Utils.isWidgetClickable(wm, widgetId)) {
                    val selectCity = Intent(context, CitySelectionActivity::class.java)
                    val pi: PendingIntent = PendingIntent.getActivity(context, 0, selectCity, 0)
                    rv.setPendingIntentTemplate(R.id.world_city_list, pi)
                }
            }

            return rv
        }

        /**
         * Inflate an offscreen copy of the widget views. Binary search through the range of sizes
         * until the optimal sizes that fit within the widget bounds are located.
         */
        private fun optimizeSizes(
            context: Context,
            template: Sizes,
            nextAlarmTime: String?
        ): Sizes {
            // Inflate a test layout to compute sizes at different font sizes.
            val inflater: LayoutInflater = LayoutInflater.from(context)
            @SuppressLint("InflateParams") val sizer: View =
                    inflater.inflate(R.layout.digital_widget_sizer, null /* root */)

            // Configure the date to display the current date string.
            val dateFormat: CharSequence = getDateFormat(context)
            val date: TextClock = sizer.findViewById(R.id.date) as TextClock
            date.setFormat12Hour(dateFormat)
            date.setFormat24Hour(dateFormat)

            // Configure the next alarm views to display the next alarm time or be gone.
            val nextAlarmIcon: TextView = sizer.findViewById(R.id.nextAlarmIcon) as TextView
            val nextAlarm: TextView = sizer.findViewById(R.id.nextAlarm) as TextView
            if (TextUtils.isEmpty(nextAlarmTime)) {
                nextAlarm.setVisibility(GONE)
                nextAlarmIcon.setVisibility(GONE)
            } else {
                nextAlarm.setText(nextAlarmTime)
                nextAlarm.setVisibility(VISIBLE)
                nextAlarmIcon.setVisibility(VISIBLE)
                nextAlarmIcon.setTypeface(UiDataModel.uiDataModel.alarmIconTypeface)
            }

            // Measure the widget at the largest possible size.
            var high = measure(template, template.largestClockFontSizePx, sizer)
            if (!high.hasViolations()) {
                return high
            }

            // Measure the widget at the smallest possible size.
            var low = measure(template, template.smallestClockFontSizePx, sizer)
            if (low.hasViolations()) {
                return low
            }

            // Binary search between the smallest and largest sizes until an optimum size is found.
            while (low.clockFontSizePx != high.clockFontSizePx) {
                val midFontSize: Int = (low.clockFontSizePx + high.clockFontSizePx) / 2
                if (midFontSize == low.clockFontSizePx) {
                    return low
                }
                val midSize = measure(template, midFontSize, sizer)
                if (midSize.hasViolations()) {
                    high = midSize
                } else {
                    low = midSize
                }
            }

            return low
        }

        private fun getAlarmManager(context: Context): AlarmManager {
            return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        }

        /**
         * Compute all font and icon sizes based on the given `clockFontSize` and apply them to
         * the offscreen `sizer` view. Measure the `sizer` view and return the resulting
         * size measurements.
         */
        private fun measure(template: Sizes, clockFontSize: Int, sizer: View): Sizes {
            // Create a copy of the given template sizes.
            val measuredSizes = template.newSize()

            // Configure the clock to display the widest time string.
            val date: TextClock = sizer.findViewById(R.id.date) as TextClock
            val clock: TextClock = sizer.findViewById(R.id.clock) as TextClock
            val nextAlarm: TextView = sizer.findViewById(R.id.nextAlarm) as TextView
            val nextAlarmIcon: TextView = sizer.findViewById(R.id.nextAlarmIcon) as TextView

            // Adjust the font sizes.
            measuredSizes.clockFontSizePx = clockFontSize
            clock.setText(getLongestTimeString(clock))
            clock.setTextSize(COMPLEX_UNIT_PX, measuredSizes.mClockFontSizePx.toFloat())
            date.setTextSize(COMPLEX_UNIT_PX, measuredSizes.mFontSizePx.toFloat())
            nextAlarm.setTextSize(COMPLEX_UNIT_PX, measuredSizes.mFontSizePx.toFloat())
            nextAlarmIcon.setTextSize(COMPLEX_UNIT_PX, measuredSizes.mIconFontSizePx.toFloat())
            nextAlarmIcon
                    .setPadding(measuredSizes.mIconPaddingPx, 0, measuredSizes.mIconPaddingPx, 0)

            // Measure and layout the sizer.
            val widthSize: Int = View.MeasureSpec.getSize(measuredSizes.mTargetWidthPx)
            val heightSize: Int = View.MeasureSpec.getSize(measuredSizes.mTargetHeightPx)
            val widthMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(widthSize, UNSPECIFIED)
            val heightMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(heightSize, UNSPECIFIED)
            sizer.measure(widthMeasureSpec, heightMeasureSpec)
            sizer.layout(0, 0, sizer.getMeasuredWidth(), sizer.getMeasuredHeight())

            // Copy the measurements into the result object.
            measuredSizes.mMeasuredWidthPx = sizer.getMeasuredWidth()
            measuredSizes.mMeasuredHeightPx = sizer.getMeasuredHeight()
            measuredSizes.mMeasuredTextClockWidthPx = clock.getMeasuredWidth()
            measuredSizes.mMeasuredTextClockHeightPx = clock.getMeasuredHeight()

            // If an alarm icon is required, generate one from the TextView with the special font.
            if (nextAlarmIcon.getVisibility() == VISIBLE) {
                measuredSizes.mIconBitmap = Utils.createBitmap(nextAlarmIcon)
            }

            return measuredSizes
        }

        /**
         * @return "11:59" or "23:59" in the current locale
         */
        private fun getLongestTimeString(clock: TextClock): CharSequence {
            val format: CharSequence = if (clock.is24HourModeEnabled()) {
                clock.getFormat24Hour()
            } else {
                clock.getFormat12Hour()
            }
            val longestPMTime = Calendar.getInstance()
            longestPMTime[0, 0, 0, 23] = 59
            return DateFormat.format(format, longestPMTime)
        }

        /**
         * @return the locale-specific date pattern
         */
        private fun getDateFormat(context: Context): String {
            val locale = Locale.getDefault()
            val skeleton: String = context.getString(R.string.abbrev_wday_month_day_no_year)
            return DateFormat.getBestDateTimePattern(locale, skeleton)
        }
    }
}