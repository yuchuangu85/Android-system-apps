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

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.format.DateUtils
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.Button
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.android.deskclock.data.City
import com.android.deskclock.data.CityListener
import com.android.deskclock.data.DataModel
import com.android.deskclock.events.Events
import com.android.deskclock.uidata.UiDataModel
import com.android.deskclock.worldclock.CitySelectionActivity

import java.util.Calendar
import java.util.TimeZone

/**
 * Fragment that shows the clock (analog or digital), the next alarm info and the world clock.
 */
class ClockFragment : DeskClockFragment(UiDataModel.Tab.CLOCKS) {
    // Updates dates in the UI on every quarter-hour.
    private val mQuarterHourUpdater: Runnable = QuarterHourRunnable()

    // Updates the UI in response to changes to the scheduled alarm.
    private var mAlarmChangeReceiver: BroadcastReceiver? = null

    // Detects changes to the next scheduled alarm pre-L.
    private var mAlarmObserver: ContentObserver? = null

    private var mDigitalClock: TextClock? = null
    private var mAnalogClock: AnalogClock? = null
    private var mClockFrame: View? = null
    private lateinit var mCityAdapter: SelectedCitiesAdapter
    private lateinit var mCityList: RecyclerView
    private lateinit var mDateFormat: String
    private lateinit var mDateFormatForAccessibility: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mAlarmObserver = if (Utils.isPreL) AlarmObserverPreL() else null
        mAlarmChangeReceiver = if (Utils.isLOrLater) AlarmChangedBroadcastReceiver() else null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        icicle: Bundle?
    ): View? {
        super.onCreateView(inflater, container, icicle)

        val fragmentView = inflater.inflate(R.layout.clock_fragment, container, false)

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year)
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year)

        mCityAdapter = SelectedCitiesAdapter(requireActivity(), mDateFormat,
                mDateFormatForAccessibility)

        mCityList = fragmentView.findViewById<View>(R.id.cities) as RecyclerView
        mCityList.setLayoutManager(LinearLayoutManager(requireActivity()))
        mCityList.setAdapter(mCityAdapter)
        mCityList.setItemAnimator(null)
        DataModel.dataModel.addCityListener(mCityAdapter)

        val scrollPositionWatcher = ScrollPositionWatcher()
        mCityList.addOnScrollListener(scrollPositionWatcher)

        val context = container!!.context
        mCityList.setOnTouchListener(CityListOnLongClickListener(context))
        fragmentView.setOnLongClickListener(StartScreenSaverListener())

        // On tablet landscape, the clock frame will be a distinct view. Otherwise, it'll be added
        // on as a header to the main listview.
        mClockFrame = fragmentView.findViewById(R.id.main_clock_left_pane)
        if (mClockFrame != null) {
            mDigitalClock = mClockFrame!!.findViewById<View>(R.id.digital_clock) as TextClock
            mAnalogClock = mClockFrame!!.findViewById<View>(R.id.analog_clock) as AnalogClock
            Utils.setClockIconTypeface(mClockFrame)
            Utils.updateDate(mDateFormat, mDateFormatForAccessibility, mClockFrame)
            Utils.setClockStyle(mDigitalClock!!, mAnalogClock!!)
            Utils.setClockSecondsEnabled(mDigitalClock!!, mAnalogClock!!)
        }

        // Schedule a runnable to update the date every quarter hour.
        UiDataModel.uiDataModel.addQuarterHourCallback(mQuarterHourUpdater)

        return fragmentView
    }

    override fun onResume() {
        super.onResume()

        val activity = requireActivity()

        mDateFormat = getString(R.string.abbrev_wday_month_day_no_year)
        mDateFormatForAccessibility = getString(R.string.full_wday_month_day_no_year)

        // Watch for system events that effect clock time or format.
        if (mAlarmChangeReceiver != null) {
            val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            activity.registerReceiver(mAlarmChangeReceiver, filter)
        }

        // Resume can be invoked after changing the clock style or seconds display.
        if (mDigitalClock != null && mAnalogClock != null) {
            Utils.setClockStyle(mDigitalClock!!, mAnalogClock!!)
            Utils.setClockSecondsEnabled(mDigitalClock!!, mAnalogClock!!)
        }

        val view = view
        if (view?.findViewById<View?>(R.id.main_clock_left_pane) != null) {
            // Center the main clock frame by hiding the world clocks when none are selected.
            mCityList.setVisibility(if (mCityAdapter.getItemCount() == 0) {
                View.GONE
            } else {
                View.VISIBLE
            })
        }

        refreshAlarm()

        // Alarm observer is null on L or later.
        mAlarmObserver?.let {
            val uri = Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED)
            activity.contentResolver.registerContentObserver(uri, false, it)
        }
    }

    override fun onPause() {
        super.onPause()

        val activity = requireActivity()
        if (mAlarmChangeReceiver != null) {
            activity.unregisterReceiver(mAlarmChangeReceiver)
        }
        if (mAlarmObserver != null) {
            activity.contentResolver.unregisterContentObserver(mAlarmObserver!!)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        UiDataModel.uiDataModel.removePeriodicCallback(mQuarterHourUpdater)
        DataModel.dataModel.removeCityListener(mCityAdapter)
    }

    override fun onFabClick(fab: ImageView) {
        startActivity(Intent(requireActivity(), CitySelectionActivity::class.java))
    }

    override fun onUpdateFab(fab: ImageView) {
        fab.visibility = View.VISIBLE
        fab.setImageResource(R.drawable.ic_public)
        fab.contentDescription = fab.resources.getString(R.string.button_cities)
    }

    override fun onUpdateFabButtons(left: Button, right: Button) {
        left.visibility = View.INVISIBLE
        right.visibility = View.INVISIBLE
    }

    /**
     * Refresh the next alarm time.
     */
    private fun refreshAlarm() {
        if (mClockFrame != null) {
            Utils.refreshAlarm(requireActivity(), mClockFrame)
        } else {
            mCityAdapter.refreshAlarm()
        }
    }

    /**
     * Long pressing over the main clock starts the screen saver.
     */
    private inner class StartScreenSaverListener : View.OnLongClickListener {
        override fun onLongClick(view: View): Boolean {
            startActivity(Intent(requireActivity(), ScreensaverActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_deskclock))
            return true
        }
    }

    /**
     * Long pressing over the city list starts the screen saver.
     */
    private inner class CityListOnLongClickListener(
        context: Context
    ) : SimpleOnGestureListener(), View.OnTouchListener {
        private val mGestureDetector = GestureDetector(context, this)

        override fun onLongPress(e: MotionEvent) {
            val view = view
            view?.performLongClick()
        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return mGestureDetector.onTouchEvent(event)
        }
    }

    /**
     * This runnable executes at every quarter-hour (e.g. 1:00, 1:15, 1:30, 1:45, etc...) and
     * updates the dates displayed within the UI. Quarter-hour increments were chosen to accommodate
     * the "weirdest" timezones (e.g. Nepal is UTC/GMT +05:45).
     */
    private inner class QuarterHourRunnable : Runnable {
        override fun run() {
            mCityAdapter.notifyDataSetChanged()
        }
    }

    /**
     * Prior to L, a ContentObserver was used to monitor changes to the next scheduled alarm.
     * In L and beyond this is accomplished via a system broadcast of
     * [AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED].
     */
    private inner class AlarmObserverPreL : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            refreshAlarm()
        }
    }

    /**
     * Update the display of the scheduled alarm as it changes.
     */
    private inner class AlarmChangedBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshAlarm()
        }
    }

    /**
     * Updates the vertical scroll state of this tab in the [UiDataModel] as the user scrolls
     * the recyclerview or when the size/position of elements within the recyclerview changes.
     */
    private inner class ScrollPositionWatcher
        : RecyclerView.OnScrollListener(), View.OnLayoutChangeListener {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            setTabScrolledToTop(Utils.isScrolledToTop(mCityList))
        }

        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            setTabScrolledToTop(Utils.isScrolledToTop(mCityList))
        }
    }

    /**
     * This adapter lists all of the selected world clocks. Optionally, it also includes a clock at
     * the top for the home timezone if "Automatic home clock" is turned on in settings and the
     * current time at home does not match the current time in the timezone of the current location.
     * If the phone is in portrait mode it will also include the main clock at the top.
     */
    private class SelectedCitiesAdapter(
        private val mContext: Context,
        private val mDateFormat: String?,
        private val mDateFormatForAccessibility: String?
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), CityListener {
        private val mInflater = LayoutInflater.from(mContext)
        private val mIsPortrait: Boolean = Utils.isPortrait(mContext)
        private val mShowHomeClock: Boolean = DataModel.dataModel.showHomeClock

        override fun getItemViewType(position: Int): Int {
            return if (position == 0 && mIsPortrait) {
                MAIN_CLOCK
            } else WORLD_CLOCK
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = mInflater.inflate(viewType, parent, false)
            return when (viewType) {
                WORLD_CLOCK -> CityViewHolder(view)
                MAIN_CLOCK -> MainClockViewHolder(view)
                else -> throw IllegalArgumentException("View type not recognized")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val viewType = getItemViewType(position)) {
                WORLD_CLOCK -> {
                    // Retrieve the city to bind.
                    val city: City
                    // If showing home clock, put it at the top
                    city = if (mShowHomeClock && position == (if (mIsPortrait) 1 else 0)) {
                        homeCity
                    } else {
                        val positionAdjuster = ((if (mIsPortrait) 1 else 0) +
                                if (mShowHomeClock) 1 else 0)
                        cities[position - positionAdjuster]
                    }
                    (holder as CityViewHolder).bind(mContext, city, position, mIsPortrait)
                }
                MAIN_CLOCK -> (holder as MainClockViewHolder).bind(mContext, mDateFormat,
                        mDateFormatForAccessibility, getItemCount() > 1)
                else -> throw IllegalArgumentException("Unexpected view type: $viewType")
            }
        }

        override fun getItemCount(): Int {
            val mainClockCount = if (mIsPortrait) 1 else 0
            val homeClockCount = if (mShowHomeClock) 1 else 0
            val worldClockCount = cities.size
            return mainClockCount + homeClockCount + worldClockCount
        }

        private val homeCity: City
            get() = DataModel.dataModel.homeCity

        private val cities: List<City>
            get() = DataModel.dataModel.selectedCities as List<City>

        fun refreshAlarm() {
            if (mIsPortrait && getItemCount() > 0) {
                notifyItemChanged(0)
            }
        }

        override fun citiesChanged(oldCities: List<City>, newCities: List<City>) {
            notifyDataSetChanged()
        }

        private class CityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val mName: TextView = itemView.findViewById(R.id.city_name)
            private val mDigitalClock: TextClock = itemView.findViewById(R.id.digital_clock)
            private val mAnalogClock: AnalogClock = itemView.findViewById(R.id.analog_clock)
            private val mHoursAhead: TextView = itemView.findViewById(R.id.hours_ahead)

            fun bind(context: Context, city: City, position: Int, isPortrait: Boolean) {
                val cityTimeZoneId: String = city.timeZone.id

                // Configure the digital clock or analog clock depending on the user preference.
                if (DataModel.dataModel.clockStyle == DataModel.ClockStyle.ANALOG) {
                    mDigitalClock.visibility = View.GONE
                    mAnalogClock.visibility = View.VISIBLE
                    mAnalogClock.setTimeZone(cityTimeZoneId)
                    mAnalogClock.enableSeconds(false)
                } else {
                    mAnalogClock.visibility = View.GONE
                    mDigitalClock.visibility = View.VISIBLE
                    mDigitalClock.timeZone = cityTimeZoneId
                    mDigitalClock.format12Hour = Utils.get12ModeFormat(0.3f, false)
                    mDigitalClock.format24Hour = Utils.get24ModeFormat(false)
                }

                // Supply top and bottom padding dynamically.
                val res = context.resources
                val padding = res.getDimensionPixelSize(R.dimen.medium_space_top)
                val top = if (position == 0 && !isPortrait) 0 else padding
                val left: Int = itemView.paddingLeft
                val right: Int = itemView.paddingRight
                val bottom: Int = itemView.paddingBottom
                itemView.setPadding(left, top, right, bottom)

                // Bind the city name.
                mName.text = city.name

                // Compute if the city week day matches the weekday of the current timezone.
                val localCal = Calendar.getInstance(TimeZone.getDefault())
                val cityCal: Calendar = Calendar.getInstance(city.timeZone)
                val displayDayOfWeek =
                        localCal[Calendar.DAY_OF_WEEK] != cityCal[Calendar.DAY_OF_WEEK]

                // Compare offset from UTC time on today's date (daylight savings time, etc.)
                val currentTimeZone = TimeZone.getDefault()
                val cityTimeZone = TimeZone.getTimeZone(cityTimeZoneId)
                val currentTimeMillis = System.currentTimeMillis()
                val currentUtcOffset = currentTimeZone.getOffset(currentTimeMillis).toLong()
                val cityUtcOffset = cityTimeZone.getOffset(currentTimeMillis).toLong()
                val offsetDelta = cityUtcOffset - currentUtcOffset

                val hoursDifferent = (offsetDelta / DateUtils.HOUR_IN_MILLIS).toInt()
                val minutesDifferent = (offsetDelta / DateUtils.MINUTE_IN_MILLIS).toInt() % 60
                val displayMinutes = offsetDelta % DateUtils.HOUR_IN_MILLIS != 0L
                val isAhead = hoursDifferent > 0 || (hoursDifferent == 0 &&
                        minutesDifferent > 0)
                if (!Utils.isLandscape(context)) {
                    // Bind the number of hours ahead or behind, or hide if the time is the same.
                    val displayDifference = hoursDifferent != 0 || displayMinutes
                    mHoursAhead.visibility = if (displayDifference) View.VISIBLE else View.GONE
                    val timeString = Utils.createHoursDifferentString(
                            context, displayMinutes, isAhead, hoursDifferent, minutesDifferent)
                    mHoursAhead.text = if (displayDayOfWeek) {
                        context.getString(if (isAhead) {
                            R.string.world_hours_tomorrow
                        } else {
                            R.string.world_hours_yesterday
                        }, timeString)
                    } else {
                        timeString
                    }
                } else {
                    // Only tomorrow/yesterday should be shown in landscape view.
                    mHoursAhead.visibility = if (displayDayOfWeek) View.VISIBLE else View.GONE
                    if (displayDayOfWeek) {
                        mHoursAhead.text = context.getString(if (isAhead) {
                            R.string.world_tomorrow
                        } else {
                            R.string.world_yesterday
                        })
                    }
                }
            }
        }

        private class MainClockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val mHairline: View = itemView.findViewById(R.id.hairline)
            private val mDigitalClock: TextClock = itemView.findViewById(R.id.digital_clock)
            private val mAnalogClock: AnalogClock = itemView.findViewById(R.id.analog_clock)

            init {
                Utils.setClockIconTypeface(itemView)
            }

            fun bind(
                context: Context,
                dateFormat: String?,
                dateFormatForAccessibility: String?,
                showHairline: Boolean
            ) {
                Utils.refreshAlarm(context, itemView)

                Utils.updateDate(dateFormat, dateFormatForAccessibility, itemView)
                Utils.setClockStyle(mDigitalClock, mAnalogClock)
                mHairline.visibility = if (showHairline) View.VISIBLE else View.GONE

                Utils.setClockSecondsEnabled(mDigitalClock, mAnalogClock)
            }
        }

        companion object {
            private const val MAIN_CLOCK = R.layout.main_clock_frame
            private const val WORLD_CLOCK = R.layout.world_clock_item
        }
    }
}