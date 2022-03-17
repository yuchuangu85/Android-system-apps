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

import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory

import com.android.deskclock.LogUtils
import com.android.deskclock.R
import com.android.deskclock.Utils
import com.android.deskclock.data.City
import com.android.deskclock.data.DataModel

import java.util.ArrayList
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * This factory produces entries in the world cities list view displayed at the bottom of the
 * digital widget. Each row is comprised of two world cities located side-by-side.
 */
class DigitalAppWidgetCityViewsFactory(context: Context, intent: Intent) : RemoteViewsFactory {
    private val mFillInIntent: Intent = Intent()
    private val mContext: Context = context
    private val m12HourFontSize: Float
    private val m24HourFontSize: Float
    private val mWidgetId: Int = intent.getIntExtra(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID)
    private var mFontScale = 1f
    private var mHomeCity: City? = null
    private var mShowHomeClock = false
    private var mCities: List<City>? = emptyList()

    init {
        val res: Resources = context.getResources()
        m12HourFontSize = res.getDimension(R.dimen.digital_widget_city_12_medium_font_size)
        m24HourFontSize = res.getDimension(R.dimen.digital_widget_city_24_medium_font_size)
    }

    override fun onCreate() {
        LOGGER.i("DigitalAppWidgetCityViewsFactory onCreate $mWidgetId")
    }

    override fun onDestroy() {
        LOGGER.i("DigitalAppWidgetCityViewsFactory onDestroy $mWidgetId")
    }

    /**
     * Synchronized to ensure single-threaded reading/writing of mCities, mHomeCity and
     * mShowHomeClock.
     *
     * {@inheritDoc}
     */
    @Synchronized
    override fun getCount(): Int {
        val homeClockCount = if (mShowHomeClock) 1 else 0
        val worldClockCount = mCities!!.size
        val totalClockCount = homeClockCount + worldClockCount.toDouble()

        // Number of clocks / 2 clocks per row
        return Math.ceil(totalClockCount / 2).toInt()
    }

    /**
     * Synchronized to ensure single-threaded reading/writing of mCities, mHomeCity and
     * mShowHomeClock.
     *
     * {@inheritDoc}
     */
    @Synchronized
    override fun getViewAt(position: Int): RemoteViews {
        val homeClockOffset = if (mShowHomeClock) -1 else 0
        val leftIndex = position * 2 + homeClockOffset
        val rightIndex = leftIndex + 1
        val left = when {
            leftIndex == -1 -> mHomeCity
            leftIndex < mCities!!.size -> mCities!![leftIndex]
            else -> null
        }
        val right = if (rightIndex < mCities!!.size) mCities!![rightIndex] else null
        val rv = RemoteViews(mContext.getPackageName(), R.layout.world_clock_remote_list_item)

        // Show the left clock if one exists.
        if (left != null) {
            update(rv, left, R.id.left_clock, R.id.city_name_left, R.id.city_day_left)
        } else {
            hide(rv, R.id.left_clock, R.id.city_name_left, R.id.city_day_left)
        }

        // Show the right clock if one exists.
        if (right != null) {
            update(rv, right, R.id.right_clock, R.id.city_name_right, R.id.city_day_right)
        } else {
            hide(rv, R.id.right_clock, R.id.city_name_right, R.id.city_day_right)
        }

        // Hide last spacer in last row; show for all others.
        val lastRow = position == count - 1
        rv.setViewVisibility(R.id.city_spacer, if (lastRow) View.GONE else View.VISIBLE)
        rv.setOnClickFillInIntent(R.id.widget_item, mFillInIntent)
        return rv
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    /**
     * Synchronized to ensure single-threaded reading/writing of mCities, mHomeCity and
     * mShowHomeClock.
     *
     * {@inheritDoc}
     */
    @Synchronized
    override fun onDataSetChanged() {
        // Fetch the data on the main Looper.
        val refreshRunnable = RefreshRunnable()
        DataModel.dataModel.run(refreshRunnable)

        // Store the data in local variables.
        mHomeCity = refreshRunnable.mHomeCity
        mCities = refreshRunnable.mCities
        mShowHomeClock = refreshRunnable.mShowHomeClock
        mFontScale = WidgetUtils.getScaleRatio(mContext, null, mWidgetId, mCities!!.size)
    }

    private fun update(rv: RemoteViews, city: City, clockId: Int, labelId: Int, dayId: Int) {
        rv.setCharSequence(clockId, "setFormat12Hour", Utils.get12ModeFormat(0.4f, false))
        rv.setCharSequence(clockId, "setFormat24Hour", Utils.get24ModeFormat(false))

        val is24HourFormat: Boolean = DateFormat.is24HourFormat(mContext)
        val fontSize = if (is24HourFormat) m24HourFontSize else m12HourFontSize
        rv.setTextViewTextSize(clockId, TypedValue.COMPLEX_UNIT_PX, fontSize * mFontScale)
        rv.setString(clockId, "setTimeZone", city.timeZone.id)
        rv.setTextViewText(labelId, city.name)

        // Compute if the city week day matches the weekday of the current timezone.
        val localCal = Calendar.getInstance(TimeZone.getDefault())
        val cityCal = Calendar.getInstance(city.timeZone)
        val displayDayOfWeek = localCal[Calendar.DAY_OF_WEEK] != cityCal[Calendar.DAY_OF_WEEK]

        // Bind the week day display.
        if (displayDayOfWeek) {
            val locale = Locale.getDefault()
            val weekday = cityCal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale)
            val slashDay: String = mContext.getString(R.string.world_day_of_week_label, weekday)
            rv.setTextViewText(dayId, slashDay)
        }

        rv.setViewVisibility(dayId, if (displayDayOfWeek) View.VISIBLE else View.GONE)
        rv.setViewVisibility(clockId, View.VISIBLE)
        rv.setViewVisibility(labelId, View.VISIBLE)
    }

    private fun hide(clock: RemoteViews, clockId: Int, labelId: Int, dayId: Int) {
        clock.setViewVisibility(dayId, View.INVISIBLE)
        clock.setViewVisibility(clockId, View.INVISIBLE)
        clock.setViewVisibility(labelId, View.INVISIBLE)
    }

    /**
     * This Runnable fetches data for this factory on the main thread to ensure all DataModel reads
     * occur on the main thread.
     */
    private class RefreshRunnable : Runnable {
        var mHomeCity: City? = null
        var mCities: List<City>? = null
        var mShowHomeClock = false

        override fun run() {
            mHomeCity = DataModel.dataModel.homeCity
            mCities = ArrayList(DataModel.dataModel.selectedCities)
            mShowHomeClock = DataModel.dataModel.showHomeClock
        }
    }

    companion object {
        private val LOGGER = LogUtils.Logger("DigWidgetViewsFactory")
    }
}
