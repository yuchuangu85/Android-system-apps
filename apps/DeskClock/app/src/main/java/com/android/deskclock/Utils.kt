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
import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.ArraySet
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import androidx.annotation.AnyRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.os.BuildCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat

import com.android.deskclock.data.DataModel
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.uidata.UiDataModel

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import kotlin.math.abs
import kotlin.math.max

object Utils {
    /**
     * [Uri] signifying the "silent" ringtone.
     */
    @JvmField
    val RINGTONE_SILENT = Uri.EMPTY

    fun enforceMainLooper() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw IllegalAccessError("May only call from main thread.")
        }
    }

    fun enforceNotMainLooper() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            throw IllegalAccessError("May not call from main thread.")
        }
    }

    fun indexOf(array: Array<out Any>, item: Any): Int {
        for (i in array.indices) {
            if (array[i] == item) {
                return i
            }
        }
        return -1
    }

    /**
     * @return `true` if the device is prior to [Build.VERSION_CODES.LOLLIPOP]
     */
    val isPreL: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP

    /**
     * @return `true` if the device is [Build.VERSION_CODES.LOLLIPOP] or
     * [Build.VERSION_CODES.LOLLIPOP_MR1]
     */
    val isLOrLMR1: Boolean
        get() {
            val sdkInt = Build.VERSION.SDK_INT
            return sdkInt == Build.VERSION_CODES.LOLLIPOP ||
                    sdkInt == Build.VERSION_CODES.LOLLIPOP_MR1
        }

    /**
     * @return `true` if the device is [Build.VERSION_CODES.LOLLIPOP] or later
     */
    val isLOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    /**
     * @return `true` if the device is [Build.VERSION_CODES.LOLLIPOP_MR1] or later
     */
    val isLMR1OrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

    /**
     * @return `true` if the device is [Build.VERSION_CODES.M] or later
     */
    val isMOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    /**
     * @return `true` if the device is [Build.VERSION_CODES.N] or later
     */
    val isNOrLater: Boolean
        get() = BuildCompat.isAtLeastN()

    /**
     * @return `true` if the device is [Build.VERSION_CODES.N_MR1] or later
     */
    val isNMR1OrLater: Boolean
        get() = BuildCompat.isAtLeastNMR1()

    /**
     * @return `true` if the device is [Build.VERSION_CODES.O] or later
     */
    val isOOrLater: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * @param resourceId identifies an application resource
     * @return the Uri by which the application resource is accessed
     */
    fun getResourceUri(context: Context, @AnyRes resourceId: Int): Uri {
        return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .path(resourceId.toString())
                .build()
    }

    /**
     * @param view the scrollable view to test
     * @return `true` iff the `view` content is currently scrolled to the top
     */
    fun isScrolledToTop(view: View): Boolean {
        return !view.canScrollVertically(-1)
    }

    /**
     * Calculate the amount by which the radius of a CircleTimerView should be offset by any
     * of the extra painted objects.
     */
    fun calculateRadiusOffset(
        strokeSize: Float,
        dotStrokeSize: Float,
        markerStrokeSize: Float
    ): Float {
        return max(strokeSize, max(dotStrokeSize, markerStrokeSize))
    }

    /**
     * Configure the clock that is visible to display seconds. The clock that is not visible never
     * displays seconds to avoid it scheduling unnecessary ticking runnables.
     */
    fun setClockSecondsEnabled(digitalClock: TextClock, analogClock: AnalogClock) {
        val displaySeconds: Boolean = DataModel.dataModel.displayClockSeconds
        when (DataModel.dataModel.clockStyle) {
            DataModel.ClockStyle.ANALOG -> {
                setTimeFormat(digitalClock, false)
                analogClock.enableSeconds(displaySeconds)
            }
            DataModel.ClockStyle.DIGITAL -> {
                analogClock.enableSeconds(false)
                setTimeFormat(digitalClock, displaySeconds)
            }
        }
    }

    /**
     * Set whether the digital or analog clock should be displayed in the application.
     * Returns the view to be displayed.
     */
    fun setClockStyle(digitalClock: View, analogClock: View): View {
        return when (DataModel.dataModel.clockStyle) {
            DataModel.ClockStyle.ANALOG -> {
                digitalClock.visibility = View.GONE
                analogClock.visibility = View.VISIBLE
                analogClock
            }
            DataModel.ClockStyle.DIGITAL -> {
                digitalClock.visibility = View.VISIBLE
                analogClock.visibility = View.GONE
                digitalClock
            }
        }
    }

    /**
     * For screensavers to set whether the digital or analog clock should be displayed.
     * Returns the view to be displayed.
     */
    fun setScreensaverClockStyle(digitalClock: View, analogClock: View): View {
        return when (DataModel.dataModel.screensaverClockStyle) {
            DataModel.ClockStyle.ANALOG -> {
                digitalClock.visibility = View.GONE
                analogClock.visibility = View.VISIBLE
                analogClock
            }
            DataModel.ClockStyle.DIGITAL -> {
                digitalClock.visibility = View.VISIBLE
                analogClock.visibility = View.GONE
                digitalClock
            }
        }
    }

    /**
     * For screensavers to dim the lights if necessary.
     */
    fun dimClockView(dim: Boolean, clockView: View) {
        val paint = Paint()
        paint.color = Color.WHITE
        paint.colorFilter = PorterDuffColorFilter(
                if (dim) 0x40FFFFFF else -0x3f000001,
                PorterDuff.Mode.MULTIPLY)
        clockView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    /**
     * Update and return the PendingIntent corresponding to the given `intent`.
     *
     * @param context the Context in which the PendingIntent should start the service
     * @param intent an Intent describing the service to be started
     * @return a PendingIntent that will start a service
     */
    fun pendingServiceIntent(context: Context, intent: Intent): PendingIntent {
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Update and return the PendingIntent corresponding to the given `intent`.
     *
     * @param context the Context in which the PendingIntent should start the activity
     * @param intent an Intent describing the activity to be started
     * @return a PendingIntent that will start an activity
     */
    fun pendingActivityIntent(context: Context, intent: Intent): PendingIntent {
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * @return The next alarm from [AlarmManager]
     */
    fun getNextAlarm(context: Context): String? {
        return if (isPreL) getNextAlarmPreL(context) else getNextAlarmLOrLater(context)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun getNextAlarmPreL(context: Context): String {
        val cr = context.contentResolver
        return Settings.System.getString(cr, Settings.System.NEXT_ALARM_FORMATTED)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getNextAlarmLOrLater(context: Context): String? {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val info = getNextAlarmClock(am)
        if (info != null) {
            val triggerTime = info.triggerTime
            val alarmTime = Calendar.getInstance()
            alarmTime.timeInMillis = triggerTime
            return AlarmUtils.getFormattedTime(context, alarmTime)
        }

        return null
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getNextAlarmClock(am: AlarmManager): AlarmClockInfo? {
        return am.nextAlarmClock
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun updateNextAlarm(am: AlarmManager, info: AlarmClockInfo?, op: PendingIntent?) {
        am.setAlarmClock(info, op)
    }

    fun isAlarmWithin24Hours(alarmInstance: AlarmInstance): Boolean {
        val nextAlarmTime: Calendar = alarmInstance.alarmTime
        val nextAlarmTimeMillis = nextAlarmTime.timeInMillis
        return nextAlarmTimeMillis - System.currentTimeMillis() <= DateUtils.DAY_IN_MILLIS
    }

    /**
     * Clock views can call this to refresh their alarm to the next upcoming value.
     */
    fun refreshAlarm(context: Context, clock: View?) {
        val nextAlarmIconView = clock?.findViewById<View>(R.id.nextAlarmIcon) as TextView
        val nextAlarmView = clock.findViewById<View>(R.id.nextAlarm) as TextView? ?: return

        val alarm = getNextAlarm(context)
        if (!TextUtils.isEmpty(alarm)) {
            val description = context.getString(R.string.next_alarm_description, alarm)
            nextAlarmView.text = alarm
            nextAlarmView.contentDescription = description
            nextAlarmView.visibility = View.VISIBLE
            nextAlarmIconView.visibility = View.VISIBLE
            nextAlarmIconView.contentDescription = description
        } else {
            nextAlarmView.visibility = View.GONE
            nextAlarmIconView.visibility = View.GONE
        }
    }

    fun setClockIconTypeface(clock: View?) {
        val nextAlarmIconView = clock?.findViewById<View>(R.id.nextAlarmIcon) as TextView?
        nextAlarmIconView?.typeface = UiDataModel.uiDataModel.alarmIconTypeface
    }

    /**
     * Clock views can call this to refresh their date.
     */
    fun updateDate(dateSkeleton: String?, descriptionSkeleton: String?, clock: View?) {
        val dateDisplay = clock?.findViewById<View>(R.id.date) as TextView? ?: return

        val l = Locale.getDefault()
        val datePattern = DateFormat.getBestDateTimePattern(l, dateSkeleton)
        val descriptionPattern = DateFormat.getBestDateTimePattern(l, descriptionSkeleton)

        val now = Date()
        dateDisplay.text = SimpleDateFormat(datePattern, l).format(now)
        dateDisplay.visibility = View.VISIBLE
        dateDisplay.contentDescription = SimpleDateFormat(descriptionPattern, l).format(now)
    }

    /***
     * Formats the time in the TextClock according to the Locale with a special
     * formatting treatment for the am/pm label.
     *
     * @param clock TextClock to format
     * @param includeSeconds whether or not to include seconds in the clock's time
     */
    fun setTimeFormat(clock: TextClock?, includeSeconds: Boolean) {
        // Get the best format for 12 hours mode according to the locale
        clock?.format12Hour = get12ModeFormat(amPmRatio = 0.4f, includeSeconds = includeSeconds)
        // Get the best format for 24 hours mode according to the locale
        clock?.format24Hour = get24ModeFormat(includeSeconds)
    }

    /**
     * @param amPmRatio a value between 0 and 1 that is the ratio of the relative size of the
     * am/pm string to the time string
     * @param includeSeconds whether or not to include seconds in the time string
     * @return format string for 12 hours mode time, not including seconds
     */
    fun get12ModeFormat(amPmRatio: Float, includeSeconds: Boolean): CharSequence {
        var pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                if (includeSeconds) "hmsa" else "hma")
        if (amPmRatio <= 0) {
            pattern = pattern.replace("a".toRegex(), "").trim { it <= ' ' }
        }

        // Replace spaces with "Hair Space"
        pattern = pattern.replace(" ".toRegex(), "\u200A")
        // Build a spannable so that the am/pm will be formatted
        val amPmPos = pattern.indexOf('a')
        if (amPmPos == -1) {
            return pattern
        }

        val sp: Spannable = SpannableString(pattern)
        sp.setSpan(RelativeSizeSpan(amPmRatio), amPmPos, amPmPos + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.NORMAL), amPmPos, amPmPos + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(TypefaceSpan("sans-serif"), amPmPos, amPmPos + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sp
    }

    fun get24ModeFormat(includeSeconds: Boolean): CharSequence {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(),
                if (includeSeconds) "Hms" else "Hm")
    }

    /**
     * Returns string denoting the timezone hour offset (e.g. GMT -8:00)
     *
     * @param useShortForm Whether to return a short form of the header that rounds to the
     * nearest hour and excludes the "GMT" prefix
     */
    fun getGMTHourOffset(timezone: TimeZone, useShortForm: Boolean): String {
        val gmtOffset = timezone.rawOffset
        val hour = gmtOffset / DateUtils.HOUR_IN_MILLIS
        val min = abs(gmtOffset) % DateUtils.HOUR_IN_MILLIS / DateUtils.MINUTE_IN_MILLIS

        return if (useShortForm) {
            String.format(Locale.ENGLISH, "%+d", hour)
        } else {
            String.format(Locale.ENGLISH, "GMT %+d:%02d", hour, min)
        }
    }

    /**
     * Given a point in time, return the subsequent moment any of the time zones changes days.
     * e.g. Given 8:00pm on 1/1/2016 and time zones in LA and NY this method would return a Date for
     * midnight on 1/2/2016 in the NY timezone since it changes days first.
     *
     * @param time a point in time from which to compute midnight on the subsequent day
     * @param zones a collection of time zones
     * @return the nearest point in the future at which any of the time zones changes days
     */
    fun getNextDay(time: Date, zones: Collection<TimeZone>): Date {
        var next: Calendar? = null
        for (tz in zones) {
            val c = Calendar.getInstance(tz)
            c.time = time

            // Advance to the next day.
            c.add(Calendar.DAY_OF_YEAR, 1)

            // Reset the time to midnight.
            c[Calendar.HOUR_OF_DAY] = 0
            c[Calendar.MINUTE] = 0
            c[Calendar.SECOND] = 0
            c[Calendar.MILLISECOND] = 0

            if (next == null || c < next) {
                next = c
            }
        }

        return next!!.time
    }

    fun getNumberFormattedQuantityString(context: Context, id: Int, quantity: Int): String {
        val localizedQuantity = NumberFormat.getInstance().format(quantity.toLong())
        return context.resources.getQuantityString(id, quantity, localizedQuantity)
    }

    /**
     * @return `true` iff the widget is being hosted in a container where tapping is allowed
     */
    fun isWidgetClickable(widgetManager: AppWidgetManager, widgetId: Int): Boolean {
        val wo = widgetManager.getAppWidgetOptions(widgetId)
        return (wo != null &&
                wo.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1)
                != AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD)
    }

    /**
     * @return a vector-drawable inflated from the given `resId`
     */
    fun getVectorDrawable(context: Context, @DrawableRes resId: Int): VectorDrawableCompat? {
        return VectorDrawableCompat.create(context.resources, resId, context.theme)
    }

    /**
     * This method assumes the given `view` has already been layed out.
     *
     * @return a Bitmap containing an image of the `view` at its current size
     */
    fun createBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    /**
     * [ArraySet] is @hide prior to [Build.VERSION_CODES.M].
     */
    @SuppressLint("NewApi")
    fun <E> newArraySet(collection: Collection<E>): ArraySet<E> {
        val arraySet = ArraySet<E>(collection.size)
        arraySet.addAll(collection)
        return arraySet
    }

    /**
     * @param context from which to query the current device configuration
     * @return `true` if the device is currently in portrait or reverse portrait orientation
     */
    fun isPortrait(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    /**
     * @param context from which to query the current device configuration
     * @return `true` if the device is currently in landscape or reverse landscape orientation
     */
    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun now(): Long = DataModel.dataModel.elapsedRealtime()

    fun wallClock(): Long = DataModel.dataModel.currentTimeMillis()

    /**
     * @param context to obtain strings.
     * @param displayMinutes whether or not minutes should be included
     * @param isAhead `true` if the time should be marked 'ahead', else 'behind'
     * @param hoursDifferent the number of hours the time is ahead/behind
     * @param minutesDifferent the number of minutes the time is ahead/behind
     * @return String describing the hours/minutes ahead or behind
     */
    fun createHoursDifferentString(
        context: Context,
        displayMinutes: Boolean,
        isAhead: Boolean,
        hoursDifferent: Int,
        minutesDifferent: Int
    ): String {
        val timeString: String
        timeString = if (displayMinutes && hoursDifferent != 0) {
            // Both minutes and hours
            val hoursShortQuantityString = getNumberFormattedQuantityString(context,
                    R.plurals.hours_short, abs(hoursDifferent))
            val minsShortQuantityString = getNumberFormattedQuantityString(context,
                    R.plurals.minutes_short, abs(minutesDifferent))
            @StringRes val stringType = if (isAhead) {
                R.string.world_hours_minutes_ahead
            } else {
                R.string.world_hours_minutes_behind
            }
            context.getString(stringType, hoursShortQuantityString,
                    minsShortQuantityString)
        } else {
            // Minutes alone or hours alone
            val hoursQuantityString = getNumberFormattedQuantityString(
                    context, R.plurals.hours, abs(hoursDifferent))
            val minutesQuantityString = getNumberFormattedQuantityString(
                    context, R.plurals.minutes, abs(minutesDifferent))
            @StringRes val stringType = if (isAhead) {
                R.string.world_time_ahead
            } else {
                R.string.world_time_behind
            }
            context.getString(stringType, if (displayMinutes) {
                minutesQuantityString
            } else {
                hoursQuantityString
            })
        }
        return timeString
    }

    /**
     * @param context The context from which to obtain strings
     * @param hours Hours to display (if any)
     * @param minutes Minutes to display (if any)
     * @param seconds Seconds to display
     * @return Provided time formatted as a String
     */
    fun getTimeString(context: Context, hours: Int, minutes: Int, seconds: Int): String {
        if (hours != 0) {
            return context.getString(R.string.hours_minutes_seconds, hours, minutes, seconds)
        }
        return if (minutes != 0) {
            context.getString(R.string.minutes_seconds, minutes, seconds)
        } else {
            context.getString(R.string.seconds, seconds)
        }
    }

    class ClickAccessibilityDelegate @JvmOverloads constructor(
        /** The label for talkback to apply to the view  */
        private val mLabel: String,
        /** Whether or not to always make the view visible to talkback  */
        private val mIsAlwaysAccessibilityVisible: Boolean = false
    ) : AccessibilityDelegateCompat() {

        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (mIsAlwaysAccessibilityVisible) {
                info.setVisibleToUser(true)
            }
            info.addAction(AccessibilityActionCompat(
                    AccessibilityActionCompat.ACTION_CLICK.getId(), mLabel))
        }
    }
}