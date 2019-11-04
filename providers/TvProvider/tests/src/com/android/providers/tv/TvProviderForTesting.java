/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.providers.tv;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.media.tv.TvContract;
import android.net.Uri;
import java.io.File;

class TvProviderForTesting extends TvProvider {
    private static final String FAKE_SESSION_TOKEN = "TvProviderForTesting";

    String callingPackage;
    DatabaseHelper mDatabaseHelper;

    @Override
    public boolean onCreate() {
        mDatabaseHelper = new DatabaseHelperForTesting(getContext(), TvProvider.DATABASE_VERSION);
        setOpenHelper(mDatabaseHelper, false);
        return super.onCreate();
    }

    @Override
    void scheduleEpgDataCleanup() {}

    @Override
    String getCallingPackage_() {
        if (callingPackage != null) {
            return callingPackage;
        }
        return getContext().getPackageName();
    }

    @Override
    public void shutdown() {
        super.shutdown();

        if (mDatabaseHelper != null) {
            SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
            File databaseFile = new File(db.getPath());
            mDatabaseHelper.close();
            SQLiteDatabase.deleteDatabase(databaseFile);
        }
    }

    void setTransientRowHelper(TransientRowHelper helper) {
        mTransientRowHelper = helper;
    }

    DatabaseHelper getDatabaseHelper() {
        return mDatabaseHelper;
    }

    // This method is a bypass for testing to avoid async'ly updating restriction of TvProvider
    Uri insertWatchedProgramSync(ContentValues values) {
        values.put(WATCHED_PROGRAMS_COLUMN_CONSOLIDATED, 1);
        values.put(TvContract.WatchedPrograms.COLUMN_INTERNAL_SESSION_TOKEN, FAKE_SESSION_TOKEN);
        if (mDatabaseHelper == null) {
            mDatabaseHelper = DatabaseHelper.getInstance(getContext());
        }
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        long rowId = db.insert(WATCHED_PROGRAMS_TABLE, null, values);
        return TvContract.buildWatchedProgramUri(rowId);
    }

    private static class DatabaseHelperForTesting extends TvProvider.DatabaseHelper {
        private static final String DATABASE_NAME = "tvtest.db";

        private DatabaseHelperForTesting(Context context, int version) {
            super(context, DATABASE_NAME, version);
        }
    }
}
