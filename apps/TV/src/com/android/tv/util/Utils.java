/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.util;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs.Genres;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import com.android.tv.R;
import com.android.tv.TvSingletons;
import com.android.tv.common.BaseSingletons;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.util.Clock;
import com.android.tv.data.GenreItems;
import com.android.tv.data.Program;
import com.android.tv.data.StreamInfo;
import com.android.tv.data.api.Channel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** A class that includes convenience methods for accessing TvProvider database. */
public class Utils {
    private static final String TAG = "Utils";
    private static final boolean DEBUG = false;

    public static final String EXTRA_KEY_ACTION = "action";
    public static final String EXTRA_ACTION_SHOW_TV_INPUT = "show_tv_input";
    public static final String EXTRA_KEY_FROM_LAUNCHER = "from_launcher";
    public static final String EXTRA_KEY_RECORDED_PROGRAM_ID = "recorded_program_id";
    public static final String EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME = "recorded_program_seek_time";
    public static final String EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED =
            "recorded_program_pin_checked";

    private static final String PATH_CHANNEL = "channel";
    private static final String PATH_PROGRAM = "program";
    private static final String PATH_RECORDED_PROGRAM = "recorded_program";

    private static final String PREF_KEY_LAST_WATCHED_CHANNEL_ID = "last_watched_channel_id";
    private static final String PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT =
            "last_watched_channel_id_for_input_";
    private static final String PREF_KEY_LAST_WATCHED_CHANNEL_URI = "last_watched_channel_uri";
    private static final String PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID =
            "last_watched_tuner_input_id";
    private static final String PREF_KEY_RECORDING_FAILED_REASONS = "recording_failed_reasons";
    private static final String PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET =
            "failed_scheduled_recording_info_set";

    private static final int VIDEO_SD_WIDTH = 704;
    private static final int VIDEO_SD_HEIGHT = 480;
    private static final int VIDEO_HD_WIDTH = 1280;
    private static final int VIDEO_HD_HEIGHT = 720;
    private static final int VIDEO_FULL_HD_WIDTH = 1920;
    private static final int VIDEO_FULL_HD_HEIGHT = 1080;
    private static final int VIDEO_ULTRA_HD_WIDTH = 2048;
    private static final int VIDEO_ULTRA_HD_HEIGHT = 1536;

    private static final long RECORDING_FAILED_REASON_NONE = 0;
    private static final long HALF_MINUTE_MS = TimeUnit.SECONDS.toMillis(30);
    private static final long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);

    private enum AspectRatio {
        ASPECT_RATIO_4_3(4, 3),
        ASPECT_RATIO_16_9(16, 9),
        ASPECT_RATIO_21_9(21, 9);

        final int width;
        final int height;

        AspectRatio(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        @SuppressLint("DefaultLocale")
        public String toString() {
            return String.format("%d:%d", width, height);
        }
    }

    private Utils() {}

    public static String buildSelectionForIds(String idName, List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        sb.append(idName).append(" in (").append(ids.get(0));
        for (int i = 1; i < ids.size(); ++i) {
            sb.append(",").append(ids.get(i));
        }
        sb.append(")");
        return sb.toString();
    }

    @Nullable
    @WorkerThread
    public static String getInputIdForChannel(Context context, long channelId) {
        if (channelId == Channel.INVALID_ID) {
            return null;
        }
        Uri channelUri = TvContract.buildChannelUri(channelId);
        String[] projection = {TvContract.Channels.COLUMN_INPUT_ID};
        try (Cursor cursor =
                context.getContentResolver().query(channelUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return Utils.intern(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error get input id for channel", e);
        }
        return null;
    }

    public static void setLastWatchedChannel(Context context, Channel channel) {
        if (channel == null) {
            Log.e(TAG, "setLastWatchedChannel: channel cannot be null");
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_KEY_LAST_WATCHED_CHANNEL_URI, channel.getUri().toString())
                .apply();
        if (!channel.isPassthrough()) {
            long channelId = channel.getId();
            if (channel.getId() < 0) {
                throw new IllegalArgumentException("channelId should be equal to or larger than 0");
            }
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, channelId)
                    .putLong(
                            PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT + channel.getInputId(),
                            channelId)
                    .putString(PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID, channel.getInputId())
                    .apply();
        }
    }

    /** Sets recording failed reason. */
    public static void setRecordingFailedReason(Context context, int reason) {
        long reasons = getRecordingFailedReasons(context) | 0x1 << reason;
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(PREF_KEY_RECORDING_FAILED_REASONS, reasons)
                .apply();
    }

    /** Adds the info of failed scheduled recording. */
    public static void addFailedScheduledRecordingInfo(
            Context context, String scheduledRecordingInfo) {
        Set<String> failedScheduledRecordingInfoSet = getFailedScheduledRecordingInfoSet(context);
        failedScheduledRecordingInfoSet.add(scheduledRecordingInfo);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putStringSet(
                        PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET,
                        failedScheduledRecordingInfoSet)
                .apply();
    }

    /** Clears the failed scheduled recording info set. */
    public static void clearFailedScheduledRecordingInfoSet(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET)
                .apply();
    }

    /** Clears recording failed reason. */
    public static void clearRecordingFailedReason(Context context, int reason) {
        long reasons = getRecordingFailedReasons(context) & ~(0x1 << reason);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(PREF_KEY_RECORDING_FAILED_REASONS, reasons)
                .apply();
    }

    public static long getLastWatchedChannelId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID, Channel.INVALID_ID);
    }

    public static long getLastWatchedChannelIdForInput(Context context, String inputId) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(PREF_KEY_LAST_WATCHED_CHANNEL_ID_FOR_INPUT + inputId, Channel.INVALID_ID);
    }

    public static String getLastWatchedChannelUri(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LAST_WATCHED_CHANNEL_URI, null);
    }

    /** Returns the last watched tuner input id. */
    public static String getLastWatchedTunerInputId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY_LAST_WATCHED_TUNER_INPUT_ID, null);
    }

    private static long getRecordingFailedReasons(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(PREF_KEY_RECORDING_FAILED_REASONS, RECORDING_FAILED_REASON_NONE);
    }

    /** Returns the failed scheduled recordings info set. */
    public static Set<String> getFailedScheduledRecordingInfoSet(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_KEY_FAILED_SCHEDULED_RECORDING_INFO_SET, new HashSet<>());
    }

    /** Checks do recording failed reason exist. */
    public static boolean hasRecordingFailedReason(Context context, int reason) {
        long reasons = getRecordingFailedReasons(context);
        return (reasons & 0x1 << reason) != 0;
    }

    /**
     * Returns {@code true}, if {@code uri} specifies an input, which is usually generated from
     * {@link TvContract#buildChannelsUriForInput}.
     */
    public static boolean isChannelUriForInput(Uri uri) {
        return isTvUri(uri)
                && PATH_CHANNEL.equals(uri.getPathSegments().get(0))
                && !TextUtils.isEmpty(uri.getQueryParameter("input"));
    }

    /**
     * Returns {@code true}, if {@code uri} is a channel URI for a specific channel. It is copied
     * from the hidden method TvContract.isChannelUri.
     */
    public static boolean isChannelUriForOneChannel(Uri uri) {
        return isChannelUriForTunerInput(uri) || TvContract.isChannelUriForPassthroughInput(uri);
    }

    /**
     * Returns {@code true}, if {@code uri} is a channel URI for a tuner input. It is copied from
     * the hidden method TvContract.isChannelUriForTunerInput.
     */
    public static boolean isChannelUriForTunerInput(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_CHANNEL);
    }

    private static boolean isTvUri(Uri uri) {
        return uri != null
                && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && TvContract.AUTHORITY.equals(uri.getAuthority());
    }

    private static boolean isTwoSegmentUriStartingWith(Uri uri, String pathSegment) {
        List<String> pathSegments = uri.getPathSegments();
        return pathSegments.size() == 2 && pathSegment.equals(pathSegments.get(0));
    }

    /** Returns {@code true}, if {@code uri} is a programs URI. */
    public static boolean isProgramsUri(Uri uri) {
        return isTvUri(uri) && PATH_PROGRAM.equals(uri.getPathSegments().get(0));
    }

    /** Returns {@code true}, if {@code uri} is a programs URI. */
    public static boolean isRecordedProgramsUri(Uri uri) {
        return isTvUri(uri) && PATH_RECORDED_PROGRAM.equals(uri.getPathSegments().get(0));
    }

    /** Gets the info of the program on particular time. */
    @WorkerThread
    public static Program getProgramAt(Context context, long channelId, long timeMs) {
        if (channelId == Channel.INVALID_ID) {
            Log.e(TAG, "getCurrentProgramAt - channelId is invalid");
            return null;
        }
        if (context.getMainLooper().getThread().equals(Thread.currentThread())) {
            String message = "getCurrentProgramAt called on main thread";
            if (DEBUG) {
                // Generating a stack trace can be expensive, only do it in debug mode.
                Log.w(TAG, message, new IllegalStateException(message));
            } else {
                Log.w(TAG, message);
            }
        }
        Uri uri =
                TvContract.buildProgramsUriForChannel(
                        TvContract.buildChannelUri(channelId), timeMs, timeMs);
        ContentResolver resolver = context.getContentResolver();

        String[] projection = Program.PROJECTION;
        if (TvProviderUtils.checkSeriesIdColumn(context, TvContract.Programs.CONTENT_URI)) {
            if (Utils.isProgramsUri(uri)) {
                projection =
                        TvProviderUtils.addExtraColumnsToProjection(
                                projection, TvProviderUtils.EXTRA_PROGRAM_COLUMN_SERIES_ID);
            }
        }
        try (Cursor cursor = resolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return Program.fromCursor(cursor);
            }
        }
        return null;
    }

    /** Gets the info of the current program. */
    @WorkerThread
    public static Program getCurrentProgram(Context context, long channelId) {
        return getProgramAt(context, channelId, System.currentTimeMillis());
    }

    /** Returns the round off minutes when convert milliseconds to minutes. */
    public static int getRoundOffMinsFromMs(long millis) {
        // Round off the result by adding half minute to the original ms.
        return (int) TimeUnit.MILLISECONDS.toMinutes(millis + HALF_MINUTE_MS);
    }

    /**
     * Returns duration string according to the date & time format. If {@code startUtcMillis} and
     * {@code endUtcMills} are equal, formatted time will be returned instead.
     *
     * @param startUtcMillis start of duration in millis. Should be less than {code endUtcMillis}.
     * @param endUtcMillis end of duration in millis. Should be larger than {@code startUtcMillis}.
     * @param useShortFormat {@code true} if abbreviation is needed to save space. In that case,
     *     date will be omitted if duration starts from today and is less than a day. If it's
     *     necessary, {@link DateUtils#FORMAT_NUMERIC_DATE} is used otherwise.
     */
    public static String getDurationString(
            Context context, long startUtcMillis, long endUtcMillis, boolean useShortFormat) {
        return getDurationString(
                context,
                ((BaseSingletons) context.getApplicationContext()).getClock(),
                startUtcMillis,
                endUtcMillis,
                useShortFormat);
    }

    /**
     * Returns duration string according to the date & time format. If {@code startUtcMillis} and
     * {@code endUtcMills} are equal, formatted time will be returned instead.
     *
     * @param clock the clock used to get the current time.
     * @param startUtcMillis start of duration in millis. Should be less than {code endUtcMillis}.
     * @param endUtcMillis end of duration in millis. Should be larger than {@code startUtcMillis}.
     * @param useShortFormat {@code true} if abbreviation is needed to save space. In that case,
     *     date will be omitted if duration starts from today and is less than a day. If it's
     *     necessary, {@link DateUtils#FORMAT_NUMERIC_DATE} is used otherwise.
     */
    public static String getDurationString(
            Context context,
            Clock clock,
            long startUtcMillis,
            long endUtcMillis,
            boolean useShortFormat) {
        return getDurationString(
                context,
                clock.currentTimeMillis(),
                startUtcMillis,
                endUtcMillis,
                useShortFormat,
                0);
    }

    @VisibleForTesting
    static String getDurationString(
            Context context,
            long baseMillis,
            long startUtcMillis,
            long endUtcMillis,
            boolean useShortFormat,
            int flags) {
        return getDurationString(
                context,
                startUtcMillis,
                endUtcMillis,
                useShortFormat,
                !isInGivenDay(baseMillis, startUtcMillis),
                true,
                flags);
    }

    /**
     * Returns duration string according to the time format, may not contain date information. Note:
     * At least one of showDate and showTime should be true.
     */
    public static String getDurationString(
            Context context,
            long startUtcMillis,
            long endUtcMillis,
            boolean useShortFormat,
            boolean showDate,
            boolean showTime,
            int flags) {
        flags |=
                DateUtils.FORMAT_ABBREV_MONTH
                        | ((useShortFormat) ? DateUtils.FORMAT_NUMERIC_DATE : 0);
        SoftPreconditions.checkArgument(showTime || showDate);
        if (showTime) {
            flags |= DateUtils.FORMAT_SHOW_TIME;
        }
        if (showDate) {
            flags |= DateUtils.FORMAT_SHOW_DATE;
        }
        if (!showDate || (flags & DateUtils.FORMAT_SHOW_YEAR) == 0) {
            // year is not shown unless DateUtils.FORMAT_SHOW_YEAR is set explicitly
            flags |= DateUtils.FORMAT_NO_YEAR;
        }
        if (startUtcMillis != endUtcMillis && useShortFormat) {
            // Do special handling for 12:00 AM when checking if it's in the given day.
            // If it's start, it's considered as beginning of the day. (e.g. 12:00 AM - 12:30 AM)
            // If it's end, it's considered as end of the day (e.g. 11:00 PM - 12:00 AM)
            if (!isInGivenDay(startUtcMillis, endUtcMillis - 1)
                    && endUtcMillis - startUtcMillis < TimeUnit.HOURS.toMillis(11)) {
                // Do not show date for short format.
                // Subtracting one day is needed because {@link DateUtils@formatDateRange}
                // automatically shows date if the duration covers multiple days.
                return DateUtils.formatDateRange(
                        context, startUtcMillis, endUtcMillis - TimeUnit.DAYS.toMillis(1), flags);
            }
        }
        // Workaround of b/28740989.
        // Add 1 msec to endUtcMillis to avoid DateUtils' bug with a duration of 12:00AM~12:00AM.
        String dateRange = DateUtils.formatDateRange(context, startUtcMillis, endUtcMillis, flags);
        return startUtcMillis == endUtcMillis || dateRange.contains("–")
                ? dateRange
                : DateUtils.formatDateRange(context, startUtcMillis, endUtcMillis + 1, flags);
    }

    /**
     * Checks if two given time (in milliseconds) are in the same day with regard to the locale
     * timezone.
     */
    public static boolean isInGivenDay(long dayToMatchInMillis, long subjectTimeInMillis) {
        TimeZone timeZone = Calendar.getInstance().getTimeZone();
        long offset = timeZone.getRawOffset();
        if (timeZone.inDaylightTime(new Date(dayToMatchInMillis))) {
            offset += timeZone.getDSTSavings();
        }
        return Utils.floorTime(dayToMatchInMillis + offset, ONE_DAY_MS)
                == Utils.floorTime(subjectTimeInMillis + offset, ONE_DAY_MS);
    }

    /** Calculate how many days between two milliseconds. */
    public static int computeDateDifference(long startTimeMs, long endTimeMs) {
        Calendar calFrom = Calendar.getInstance();
        Calendar calTo = Calendar.getInstance();
        calFrom.setTime(new Date(startTimeMs));
        calTo.setTime(new Date(endTimeMs));
        resetCalendar(calFrom);
        resetCalendar(calTo);
        return (int) ((calTo.getTimeInMillis() - calFrom.getTimeInMillis()) / ONE_DAY_MS);
    }

    private static void resetCalendar(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    /** Returns the last millisecond of a day which the millis belongs to. */
    public static long getLastMillisecondOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(millis));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    /** Returns the last millisecond of a day which the millis belongs to. */
    public static long getFirstMillisecondOfDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(millis));
        resetCalendar(calendar);
        return calendar.getTimeInMillis();
    }

    public static String getAspectRatioString(int width, int height) {
        if (width == 0 || height == 0) {
            return "";
        }

        for (AspectRatio ratio : AspectRatio.values()) {
            if (Math.abs((float) ratio.height / ratio.width - (float) height / width) < 0.05f) {
                return ratio.toString();
            }
        }
        return "";
    }

    public static String getAspectRatioString(float videoDisplayAspectRatio) {
        if (videoDisplayAspectRatio <= 0) {
            return "";
        }

        for (AspectRatio ratio : AspectRatio.values()) {
            if (Math.abs((float) ratio.width / ratio.height - videoDisplayAspectRatio) < 0.05f) {
                return ratio.toString();
            }
        }
        return "";
    }

    public static int getVideoDefinitionLevelFromSize(int width, int height) {
        if (width >= VIDEO_ULTRA_HD_WIDTH && height >= VIDEO_ULTRA_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD;
        } else if (width >= VIDEO_FULL_HD_WIDTH && height >= VIDEO_FULL_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD;
        } else if (width >= VIDEO_HD_WIDTH && height >= VIDEO_HD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_HD;
        } else if (width >= VIDEO_SD_WIDTH && height >= VIDEO_SD_HEIGHT) {
            return StreamInfo.VIDEO_DEFINITION_LEVEL_SD;
        }
        return StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
    }

    public static String getVideoDefinitionLevelString(Context context, int videoFormat) {
        switch (videoFormat) {
            case StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD:
                return context.getResources().getString(R.string.video_definition_level_ultra_hd);
            case StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD:
                return context.getResources().getString(R.string.video_definition_level_full_hd);
            case StreamInfo.VIDEO_DEFINITION_LEVEL_HD:
                return context.getResources().getString(R.string.video_definition_level_hd);
            case StreamInfo.VIDEO_DEFINITION_LEVEL_SD:
                return context.getResources().getString(R.string.video_definition_level_sd);
        }
        return "";
    }

    public static String getAudioChannelString(Context context, int channelCount) {
        switch (channelCount) {
            case 1:
                return context.getResources().getString(R.string.audio_channel_mono);
            case 2:
                return context.getResources().getString(R.string.audio_channel_stereo);
            case 6:
                return context.getResources().getString(R.string.audio_channel_5_1);
            case 8:
                return context.getResources().getString(R.string.audio_channel_7_1);
        }
        return "";
    }

    public static boolean isEqualLanguage(String lang1, String lang2) {
        if (lang1 == null) {
            return lang2 == null;
        } else if (lang2 == null) {
            return false;
        }
        try {
            return TextUtils.equals(
                    new Locale(lang1).getISO3Language(), new Locale(lang2).getISO3Language());
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        return context.getPackageManager()
                        .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        .size()
                > 0;
    }

    /** Returns the label for a given input. Returns the custom label, if any. */
    public static String loadLabel(Context context, TvInputInfo input) {
        if (input == null) {
            return null;
        }
        TvInputManagerHelper inputManager =
                TvSingletons.getSingletons(context).getTvInputManagerHelper();
        CharSequence customLabel = inputManager.loadCustomLabel(input);
        String label = (customLabel == null) ? null : customLabel.toString();
        if (TextUtils.isEmpty(label)) {
            label = inputManager.loadLabel(input).toString();
        }
        return label;
    }

    /** Enable all channels synchronously. */
    @WorkerThread
    public static void enableAllChannels(Context context) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_BROWSABLE, 1);
        context.getContentResolver().update(Channels.CONTENT_URI, values, null, null);
    }

    /**
     * Converts time in milliseconds to a String.
     *
     * @param fullFormat {@code true} for returning date string with a full format (e.g., Mon Aug 15
     *     20:08:35 GMT 2016). {@code false} for a short format, {e.g., 8/15/16 or 8:08 AM}, in
     *     which only the time is shown if the time is on the same day as now, and only the date is
     *     shown if it's a different day.
     */
    public static String toTimeString(long timeMillis, boolean fullFormat) {
        if (fullFormat) {
            return new Date(timeMillis).toString();
        } else {
            return (String)
                    DateUtils.formatSameDayTime(
                            timeMillis,
                            System.currentTimeMillis(),
                            SimpleDateFormat.SHORT,
                            SimpleDateFormat.SHORT);
        }
    }

    /** Converts time in milliseconds to a String. */
    public static String toTimeString(long timeMillis) {
        return toTimeString(timeMillis, true);
    }

    /**
     * Returns a {@link String} object which contains the layout information of the {@code view}.
     */
    public static String toRectString(View view) {
        return "{"
                + "l="
                + view.getLeft()
                + ",r="
                + view.getRight()
                + ",t="
                + view.getTop()
                + ",b="
                + view.getBottom()
                + ",w="
                + view.getWidth()
                + ",h="
                + view.getHeight()
                + "}";
    }

    /**
     * Floors time to the given {@code timeUnit}. For example, if time is 5:32:11 and timeUnit is
     * one hour (60 * 60 * 1000), then the output will be 5:00:00.
     */
    public static long floorTime(long timeMs, long timeUnit) {
        return timeMs - (timeMs % timeUnit);
    }

    /**
     * Ceils time to the given {@code timeUnit}. For example, if time is 5:32:11 and timeUnit is one
     * hour (60 * 60 * 1000), then the output will be 6:00:00.
     */
    public static long ceilTime(long timeMs, long timeUnit) {
        return timeMs + timeUnit - (timeMs % timeUnit);
    }

    /** Returns an {@link String#intern() interned} string or null if the input is null. */
    @Nullable
    public static String intern(@Nullable String string) {
        return string == null ? null : string.intern();
    }

    /**
     * Check if the index is valid for the collection,
     *
     * @param collection the collection
     * @param index the index position to test
     * @return index >= 0 && index < collection.size().
     */
    public static boolean isIndexValid(@Nullable Collection<?> collection, int index) {
        return collection != null && (index >= 0 && index < collection.size());
    }

    /** Returns a localized version of the text resource specified by resourceId. */
    public static CharSequence getTextForLocale(Context context, Locale locale, int resourceId) {
        if (locale.equals(context.getResources().getConfiguration().locale)) {
            return context.getText(resourceId);
        }
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        return context.createConfigurationContext(config).getText(resourceId);
    }

    /** Checks where there is any internal TV input. */
    public static boolean hasInternalTvInputs(Context context, boolean tunerInputOnly) {
        for (TvInputInfo input :
                TvSingletons.getSingletons(context)
                        .getTvInputManagerHelper()
                        .getTvInputInfos(true, tunerInputOnly)) {
            if (isInternalTvInput(context, input.getId())) {
                return true;
            }
        }
        return false;
    }

    /** Returns the internal TV inputs. */
    public static List<TvInputInfo> getInternalTvInputs(Context context, boolean tunerInputOnly) {
        List<TvInputInfo> inputs = new ArrayList<>();
        for (TvInputInfo input :
                TvSingletons.getSingletons(context)
                        .getTvInputManagerHelper()
                        .getTvInputInfos(true, tunerInputOnly)) {
            if (isInternalTvInput(context, input.getId())) {
                inputs.add(input);
            }
        }
        return inputs;
    }

    /** Checks whether the input is internal or not. */
    public static boolean isInternalTvInput(Context context, String inputId) {
        ComponentName unflattenInputId = ComponentName.unflattenFromString(inputId);
        if (unflattenInputId == null) {
            return false;
        }
        return context.getPackageName().equals(unflattenInputId.getPackageName());
    }

    /** Returns the TV input for the given {@code program}. */
    @Nullable
    public static TvInputInfo getTvInputInfoForProgram(Context context, Program program) {
        if (!Program.isProgramValid(program)) {
            return null;
        }
        return getTvInputInfoForChannelId(context, program.getChannelId());
    }

    /** Returns the TV input for the given channel ID. */
    @Nullable
    public static TvInputInfo getTvInputInfoForChannelId(Context context, long channelId) {
        TvSingletons tvSingletons = TvSingletons.getSingletons(context);
        Channel channel = tvSingletons.getChannelDataManager().getChannel(channelId);
        if (channel == null) {
            return null;
        }
        return tvSingletons.getTvInputManagerHelper().getTvInputInfo(channel.getInputId());
    }

    /** Returns the {@link TvInputInfo} for the given input ID. */
    @Nullable
    public static TvInputInfo getTvInputInfoForInputId(Context context, String inputId) {
        return TvSingletons.getSingletons(context)
                .getTvInputManagerHelper()
                .getTvInputInfo(inputId);
    }

    /** Returns the canonical genre ID's from the {@code genres}. */
    public static int[] getCanonicalGenreIds(String genres) {
        if (TextUtils.isEmpty(genres)) {
            return null;
        }
        return getCanonicalGenreIds(Genres.decode(genres));
    }

    /** Returns the canonical genre ID's from the {@code genres}. */
    public static int[] getCanonicalGenreIds(String[] canonicalGenres) {
        if (canonicalGenres != null && canonicalGenres.length > 0) {
            int[] results = new int[canonicalGenres.length];
            int i = 0;
            for (String canonicalGenre : canonicalGenres) {
                int genreId = GenreItems.getId(canonicalGenre);
                if (genreId == GenreItems.ID_ALL_CHANNELS) {
                    // Skip if the genre is unknown.
                    continue;
                }
                results[i++] = genreId;
            }
            if (i < canonicalGenres.length) {
                results = Arrays.copyOf(results, i);
            }
            return results;
        }
        return null;
    }

    /** Returns the canonical genres for database. */
    public static String getCanonicalGenre(int[] canonicalGenreIds) {
        if (canonicalGenreIds == null || canonicalGenreIds.length == 0) {
            return null;
        }
        String[] genres = new String[canonicalGenreIds.length];
        for (int i = 0; i < canonicalGenreIds.length; ++i) {
            genres[i] = GenreItems.getCanonicalGenre(canonicalGenreIds[i]);
        }
        return Genres.encode(genres);
    }

    /**
     * Runs the method in main thread. If the current thread is not main thread, block it util the
     * method is finished.
     */
    public static void runInMainThreadAndWait(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            Future<?> temp = MainThreadExecutor.getInstance().submit(runnable);
            try {
                temp.get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "failed to finish the execution", e);
            }
        }
    }
}
