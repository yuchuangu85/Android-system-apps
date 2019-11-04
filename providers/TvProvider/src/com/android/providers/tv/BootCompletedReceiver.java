/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.providers.tv;

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.android.providers.tv.TvProvider.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * This will be launched when BOOT_COMPLETED intent is broadcast.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = false;
    private static final String TAG = "BootCompletedReceiver";

    private static final String[] PROJECTION = {TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME};
    private static final String WHERE = TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME + "=?";

    private final Executor mExecutor;

    public BootCompletedReceiver() {
        this(AsyncTask.SERIAL_EXECUTOR);
    }

    public BootCompletedReceiver(Executor executor) {
        super();
        mExecutor = executor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                // Delete the transient rows on boot.
                TransientRowHelper.getInstance(context).ensureOldTransientRowsDeleted();

                SQLiteDatabase db = DatabaseHelper.getInstance(context).getReadableDatabase();
                deleteRowsOfUninstalledPackages(context, db);
                pendingResult.finish();
                return null;
            }
        }.executeOnExecutor(mExecutor);
    }

    private void deleteRowsOfUninstalledPackages(Context context, SQLiteDatabase db) {
        deleteRowsOfUninstalledPackagesInternal(context, db, TvContract.Channels.CONTENT_URI);
        deleteRowsOfUninstalledPackagesInternal(
                context, db, TvContract.RecordedPrograms.CONTENT_URI);
        deleteRowsOfUninstalledPackagesInternal(
                context, db, TvContract.WatchNextPrograms.CONTENT_URI);
    }

    private void deleteRowsOfUninstalledPackagesInternal(
            Context context, SQLiteDatabase db, Uri uri) {
        if (DEBUG) {
            Log.d(TAG, "deleteRowsOfUninstalledPackages. URI=" + uri);
        }
        String tableName = getTableName(uri);
        if (tableName == null) {
            Log.w(TAG, "Warning. Invalid URI:" + uri);
            return;
        }
        List<String> packageNames = new ArrayList<>();
        try (Cursor c = db.query(
                true /* distinct */, tableName, PROJECTION, null, null, null, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String packageName = c.getString(0);
                    packageNames.add(packageName);
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Packages found from rows:" + packageNames);
        }
        PackageManager pm = context.getPackageManager();
        for (String packageName : packageNames) {
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                int result =
                        context.getContentResolver().delete(uri, WHERE, new String[] {packageName});
                Log.i(TAG, "package " + packageName + " not found. Deleted " + result + " rows.");
            }
        }
    }

    @Nullable
    private String getTableName(Uri uri) {
        if (TvContract.Channels.CONTENT_URI.equals(uri)) {
            return TvProvider.CHANNELS_TABLE;
        }
        if (TvContract.Programs.CONTENT_URI.equals(uri)) {
            return TvProvider.PROGRAMS_TABLE;
        }
        if (TvContract.PreviewPrograms.CONTENT_URI.equals(uri)) {
            return TvProvider.PREVIEW_PROGRAMS_TABLE;
        }
        if (TvContract.WatchedPrograms.CONTENT_URI.equals(uri)) {
            return TvProvider.WATCHED_PROGRAMS_TABLE;
        }
        if (TvContract.RecordedPrograms.CONTENT_URI.equals(uri)) {
            return TvProvider.RECORDED_PROGRAMS_TABLE;
        }
        if (TvContract.WatchNextPrograms.CONTENT_URI.equals(uri)) {
            return TvProvider.WATCH_NEXT_PROGRAMS_TABLE;
        }
        return null;
    }
}
