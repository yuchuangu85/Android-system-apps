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


import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public class TraceService extends IntentService {

    protected static String INTENT_ACTION_FORCE_STOP_TRACING = "com.android.traceur.FORCE_STOP_TRACING";
    private static String INTENT_ACTION_STOP_TRACING = "com.android.traceur.STOP_TRACING";
    private static String INTENT_ACTION_START_TRACING = "com.android.traceur.START_TRACING";

    private static String INTENT_EXTRA_TAGS= "tags";
    private static String INTENT_EXTRA_BUFFER = "buffer";
    private static String INTENT_EXTRA_APPS = "apps";
    private static String INTENT_EXTRA_LONG_TRACE = "long_trace";
    private static String INTENT_EXTRA_LONG_TRACE_SIZE = "long_trace_size";
    private static String INTENT_EXTRA_LONG_TRACE_DURATION = "long_trace_duration";

    private static int TRACE_NOTIFICATION = 1;
    private static int SAVING_TRACE_NOTIFICATION = 2;
    private static int FORCE_STOP_SAVING_TRACE_NOTIFICATION = 3;

    public static void startTracing(final Context context,
            Collection<String> tags, int bufferSizeKb, boolean apps,
            boolean longTrace, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes) {
        Intent intent = new Intent(context, TraceService.class);
        intent.setAction(INTENT_ACTION_START_TRACING);
        intent.putExtra(INTENT_EXTRA_TAGS, new ArrayList(tags));
        intent.putExtra(INTENT_EXTRA_BUFFER, bufferSizeKb);
        intent.putExtra(INTENT_EXTRA_APPS, apps);
        intent.putExtra(INTENT_EXTRA_LONG_TRACE, longTrace);
        intent.putExtra(INTENT_EXTRA_LONG_TRACE_SIZE, maxLongTraceSizeMb);
        intent.putExtra(INTENT_EXTRA_LONG_TRACE_DURATION, maxLongTraceDurationMinutes);
        context.startForegroundService(intent);
    }

    public static void stopTracing(final Context context) {
        Intent intent = new Intent(context, TraceService.class);
        intent.setAction(INTENT_ACTION_STOP_TRACING);
        context.startForegroundService(intent);
    }

    public TraceService() {
        this("TraceService");
    }

    protected TraceService(String name) {
        super(name);
        setIntentRedelivery(true);
    }

    @Override
    public void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();

        if (intent.getAction().equals(INTENT_ACTION_START_TRACING)) {
            startTracingInternal(intent.getStringArrayListExtra(INTENT_EXTRA_TAGS),
                intent.getIntExtra(INTENT_EXTRA_BUFFER,
                    Integer.parseInt(context.getString(R.string.default_buffer_size))),
                intent.getBooleanExtra(INTENT_EXTRA_APPS, false),
                intent.getBooleanExtra(INTENT_EXTRA_LONG_TRACE, false),
                intent.getIntExtra(INTENT_EXTRA_LONG_TRACE_SIZE,
                    Integer.parseInt(context.getString(R.string.default_long_trace_size))),
                intent.getIntExtra(INTENT_EXTRA_LONG_TRACE_DURATION,
                    Integer.parseInt(context.getString(R.string.default_long_trace_duration))));
        } else if (intent.getAction().equals(INTENT_ACTION_STOP_TRACING)) {
            stopTracingInternal(TraceUtils.getOutputFilename(), false);
        } else if (intent.getAction().equals(INTENT_ACTION_FORCE_STOP_TRACING)) {
            stopTracingInternal(TraceUtils.getOutputFilename(), true);
        }
    }

    private void startTracingInternal(Collection<String> tags, int bufferSizeKb, boolean appTracing,
            boolean longTrace, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes) {
        Context context = getApplicationContext();
        Intent stopIntent = new Intent(Receiver.STOP_ACTION,
            null, context, Receiver.class);
        stopIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        String title = context.getString(R.string.trace_is_being_recorded);
        String msg = context.getString(R.string.tap_to_stop_tracing);

        Notification.Builder notification =
            new Notification.Builder(context, Receiver.NOTIFICATION_CHANNEL_TRACING)
                .setSmallIcon(R.drawable.stat_sys_adb)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(msg)
                .setContentIntent(
                    PendingIntent.getBroadcast(context, 0, stopIntent, 0))
                .setOngoing(true)
                .setLocalOnly(true)
                .setColor(getColor(
                    com.android.internal.R.color.system_notification_accent_color));

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            notification.extend(new Notification.TvExtender());
        }

        startForeground(TRACE_NOTIFICATION, notification.build());

        if (TraceUtils.traceStart(tags, bufferSizeKb, appTracing,
                longTrace, maxLongTraceSizeMb, maxLongTraceDurationMinutes)) {
            stopForeground(Service.STOP_FOREGROUND_DETACH);
        } else {
            // Starting the trace was unsuccessful, so ensure that tracing
            // is stopped and the preference is reset.
            TraceUtils.traceStop();
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(context.getString(R.string.pref_key_tracing_on),
                        false).commit();
            QsService.updateTile();
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        }
    }

    private void stopTracingInternal(String outputFilename, boolean forceStop) {
        Context context = getApplicationContext();
        NotificationManager notificationManager =
            getSystemService(NotificationManager.class);

        Notification.Builder notification =
            new Notification.Builder(this, Receiver.NOTIFICATION_CHANNEL_OTHER)
                .setSmallIcon(R.drawable.stat_sys_adb)
                .setContentTitle(getString(R.string.saving_trace))
                .setTicker(getString(R.string.saving_trace))
                .setLocalOnly(true)
                .setProgress(1, 0, true)
                .setColor(getColor(
                    com.android.internal.R.color.system_notification_accent_color));

        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            notification.extend(new Notification.TvExtender());
        }

        // We want to do the same thing regardless of whether the trace was
        // stopped via the external signal or within Traceur. However, these
        // two stopping mechanisms must use different notification IDs so that
        // one doesn't accidentally remove or override notifications from the
        // other.
        int notificationId = forceStop
                ? FORCE_STOP_SAVING_TRACE_NOTIFICATION : SAVING_TRACE_NOTIFICATION;

        startForeground(notificationId, notification.build());

        notificationManager.cancel(TRACE_NOTIFICATION);

        File file = TraceUtils.getOutputFile(outputFilename);

        if (TraceUtils.traceDump(file)) {
            FileSender.postNotification(getApplicationContext(), file);
        }

        stopForeground(Service.STOP_FOREGROUND_REMOVE);
    }

}
