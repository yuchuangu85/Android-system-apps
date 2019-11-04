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
 * limitations under the License
 */

package com.android.traceur;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Receiver extends BroadcastReceiver {

    public static final String STOP_ACTION = "com.android.traceur.STOP";
    public static final String OPEN_ACTION = "com.android.traceur.OPEN";

    public static final String NOTIFICATION_CHANNEL_TRACING = "trace-is-being-recorded";
    public static final String NOTIFICATION_CHANNEL_OTHER = "system-tracing";

    private static final List<String> TRACE_TAGS = Arrays.asList(
            "am", "binder_driver", "camera", "dalvik", "freq", "gfx", "hal",
            "idle", "input", "res", "sched", "sync", "view", "wm",
            "workq", "memory");

    /* The user list doesn't include workq, irq, or sync, because the user builds don't have
     * permissions for them. */
    private static final List<String> TRACE_TAGS_USER = Arrays.asList(
            "am", "binder_driver", "camera", "dalvik", "freq", "gfx", "hal",
            "idle", "input", "res", "sched", "view", "wm", "memory");

    private static final String TAG = "Traceur";

    private static Set<String> mDefaultTagList = null;
    private static ContentObserver mDeveloperOptionsObserver;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            createNotificationChannels(context);
            updateDeveloperOptionsWatcher(context);

            // We know that Perfetto won't be tracing already at boot, so pass the
            // tracingIsOff argument to avoid the Perfetto check.
            updateTracing(context, /* assumeTracingIsOff= */ true);
        } else if (STOP_ACTION.equals(intent.getAction())) {
            prefs.edit().putBoolean(context.getString(R.string.pref_key_tracing_on), false).commit();
            updateTracing(context);
        } else if (OPEN_ACTION.equals(intent.getAction())) {
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            context.startActivity(new Intent(context, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /*
     * Updates the current tracing state based on the current state of preferences.
     */
    public static void updateTracing(Context context) {
        updateTracing(context, false);
    }
    public static void updateTracing(Context context, boolean assumeTracingIsOff) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean prefsTracingOn =
                prefs.getBoolean(context.getString(R.string.pref_key_tracing_on), false);

        boolean traceUtilsTracingOn = assumeTracingIsOff ? false : TraceUtils.isTracingOn();

        if (prefsTracingOn != traceUtilsTracingOn) {
            if (prefsTracingOn) {
                // Show notification if the tags in preferences are not all actually available.
                Set<String> activeAvailableTags = getActiveTags(context, prefs, true);
                Set<String> activeTags = getActiveTags(context, prefs, false);

                if (!activeAvailableTags.equals(activeTags)) {
                    postCategoryNotification(context, prefs);
                }

                int bufferSize = Integer.parseInt(
                    prefs.getString(context.getString(R.string.pref_key_buffer_size),
                        context.getString(R.string.default_buffer_size)));

                boolean appTracing = prefs.getBoolean(context.getString(R.string.pref_key_apps), true);
                boolean longTrace = prefs.getBoolean(context.getString(R.string.pref_key_long_traces), true);

                int maxLongTraceSize = Integer.parseInt(
                    prefs.getString(context.getString(R.string.pref_key_max_long_trace_size),
                        context.getString(R.string.default_long_trace_size)));

                int maxLongTraceDuration = Integer.parseInt(
                    prefs.getString(context.getString(R.string.pref_key_max_long_trace_duration),
                        context.getString(R.string.default_long_trace_duration)));

                TraceService.startTracing(context, activeAvailableTags, bufferSize,
                    appTracing, longTrace, maxLongTraceSize, maxLongTraceDuration);
            } else {
                TraceService.stopTracing(context);
            }
        }

        // Update the main UI and the QS tile.
        context.sendBroadcast(new Intent(MainFragment.ACTION_REFRESH_TAGS));
        QsService.updateTile();
    }

    /*
     * Updates the current Quick Settings tile state based on the current state
     * of preferences.
     */
    public static void updateQuickSettings(Context context) {
        boolean quickSettingsEnabled =
            PreferenceManager.getDefaultSharedPreferences(context)
              .getBoolean(context.getString(R.string.pref_key_quick_setting), false);

        ComponentName name = new ComponentName(context, QsService.class);
        context.getPackageManager().setComponentEnabledSetting(name,
            quickSettingsEnabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);

        IStatusBarService statusBarService = IStatusBarService.Stub.asInterface(
            ServiceManager.checkService(Context.STATUS_BAR_SERVICE));

        try {
            if (statusBarService != null) {
                if (quickSettingsEnabled) {
                    statusBarService.addTile(name);
                } else {
                    statusBarService.remTile(name);
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to modify QS tile for Traceur.", e);
        }

        QsService.updateTile();
    }

    /*
     * When Developer Options are toggled, also toggle the Storage Provider that
     * shows "System traces" in Files.
     * When Developer Options are turned off, reset the Show Quick Settings Tile
     * preference to false to hide the tile. The user will need to re-enable the
     * preference if they decide to turn Developer Options back on again.
     */
    private static void updateDeveloperOptionsWatcher(Context context) {
        Uri settingUri = Settings.Global.getUriFor(
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);

        ContentObserver developerOptionsObserver =
            new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);

                    boolean developerOptionsEnabled = (1 ==
                        Settings.Global.getInt(context.getContentResolver(),
                            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED , 0));

                    ComponentName name = new ComponentName(context,
                        StorageProvider.class);
                    context.getPackageManager().setComponentEnabledSetting(name,
                       developerOptionsEnabled
                            ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);

                    if (!developerOptionsEnabled) {
                        SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(context);
                        prefs.edit().putBoolean(
                            context.getString(R.string.pref_key_quick_setting), false)
                            .commit();
                        updateQuickSettings(context);
                    }
                }
            };

        context.getContentResolver().registerContentObserver(settingUri,
            false, developerOptionsObserver);
        developerOptionsObserver.onChange(true);
    }

    private static void postCategoryNotification(Context context, SharedPreferences prefs) {
        Intent sendIntent = new Intent(context, MainActivity.class);

        String title = context.getString(R.string.tracing_categories_unavailable);
        String msg = TextUtils.join(", ", getActiveUnavailableTags(context, prefs));
        final Notification.Builder builder =
            new Notification.Builder(context, NOTIFICATION_CHANNEL_OTHER)
                .setSmallIcon(R.drawable.stat_sys_adb)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(msg)
                .setContentIntent(PendingIntent.getActivity(
                        context, 0, sendIntent, PendingIntent.FLAG_ONE_SHOT
                                | PendingIntent.FLAG_CANCEL_CURRENT))
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setColor(context.getColor(
                        com.android.internal.R.color.system_notification_accent_color));

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            builder.extend(new Notification.TvExtender());
        }

        context.getSystemService(NotificationManager.class)
            .notify(Receiver.class.getName(), 0, builder.build());
    }

    private static void createNotificationChannels(Context context) {
        NotificationChannel tracingChannel = new NotificationChannel(
            NOTIFICATION_CHANNEL_TRACING,
            context.getString(R.string.trace_is_being_recorded),
            NotificationManager.IMPORTANCE_HIGH);
        tracingChannel.setBypassDnd(true);
        tracingChannel.enableVibration(true);
        tracingChannel.setSound(null, null);

        NotificationChannel saveTraceChannel = new NotificationChannel(
            NOTIFICATION_CHANNEL_OTHER,
            context.getString(R.string.saving_trace),
            NotificationManager.IMPORTANCE_HIGH);
        saveTraceChannel.setBypassDnd(true);
        saveTraceChannel.enableVibration(true);
        saveTraceChannel.setSound(null, null);

        NotificationManager notificationManager =
            context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(tracingChannel);
        notificationManager.createNotificationChannel(saveTraceChannel);
    }

    public static Set<String> getActiveTags(Context context, SharedPreferences prefs, boolean onlyAvailable) {
        Set<String> tags = prefs.getStringSet(context.getString(R.string.pref_key_tags),
                getDefaultTagList());
        Set<String> available = TraceUtils.listCategories().keySet();

        if (onlyAvailable) {
            tags.retainAll(available);
        }

        Log.v(TAG, "getActiveTags(onlyAvailable=" + onlyAvailable + ") = \"" + tags.toString() + "\"");
        return tags;
    }

    public static Set<String> getActiveUnavailableTags(Context context, SharedPreferences prefs) {
        Set<String> tags = prefs.getStringSet(context.getString(R.string.pref_key_tags),
                getDefaultTagList());
        Set<String> available = TraceUtils.listCategories().keySet();

        tags.removeAll(available);

        Log.v(TAG, "getActiveUnavailableTags() = \"" + tags.toString() + "\"");
        return tags;
    }

    public static Set<String> getDefaultTagList() {
        if (mDefaultTagList == null) {
            mDefaultTagList = new ArraySet<String>(Build.TYPE.equals("user")
                ? TRACE_TAGS_USER : TRACE_TAGS);
        }

        return mDefaultTagList;
    }
}
