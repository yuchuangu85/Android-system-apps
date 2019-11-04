/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.picker;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class PickCountRecordProvider extends ContentProvider {
    private static final String TAG = "PickCountRecordProvider";

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int URI_PICK_RECORD = 1;

    private static final String PATH_PICK_COUNT_RECORD = "pickCountRecord";

    private static final String TABLE_PICK_COUNT_RECORD = "pickCountRecordTable";

    static final String AUTHORITY = "com.android.documentsui.pickCountRecord";

    static {
        MATCHER.addURI(AUTHORITY, "pickCountRecord/*", URI_PICK_RECORD);
    }

    public static class Columns {
        public static final String FILE_HASH_ID = "file_hash_id";
        public static final String PICK_COUNT = "pick_count";
    }

    /**
     * Build pickRecord uri.
     *
     * @param hashFileId the file hash id.
     * @return return an pick record uri.
     */
    public static Uri buildPickRecordUri(int hashFileId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).appendPath(PATH_PICK_COUNT_RECORD)
                .appendPath(Integer.toString(hashFileId))
                .build();
    }

    private PickCountRecordProvider.DatabaseHelper mHelper;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "pickCountRecord.db";

        private static final int VERSION_INIT = 1;

        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, VERSION_INIT);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_PICK_COUNT_RECORD + " ("
                    + PickCountRecordProvider.Columns.FILE_HASH_ID + " TEXT NOT NULL PRIMARY KEY,"
                    + PickCountRecordProvider.Columns.PICK_COUNT + " INTEGER" + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database; wiping app data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PICK_COUNT_RECORD);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        mHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (MATCHER.match(uri) != URI_PICK_RECORD) {
            throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }
        final SQLiteDatabase db = mHelper.getReadableDatabase();
        final String fileHashId = uri.getPathSegments().get(1);
        return db.query(TABLE_PICK_COUNT_RECORD, projection, Columns.FILE_HASH_ID + "=?",
                new String[] { fileHashId }, null, null, sortOrder);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (MATCHER.match(uri) != URI_PICK_RECORD) {
            throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final ContentValues key = new ContentValues();

        final String hashId = uri.getPathSegments().get(1);
        key.put(Columns.FILE_HASH_ID, hashId);

        // Ensure that row exists, then update with changed values
        db.insertWithOnConflict(TABLE_PICK_COUNT_RECORD, null, key, SQLiteDatabase.CONFLICT_IGNORE);
        db.update(TABLE_PICK_COUNT_RECORD, values, Columns.FILE_HASH_ID + "=?",
            new String[] { hashId });
        return null;
    }

    static void setPickRecord(ContentResolver resolver, int fileHashId, int pickCount) {
        final ContentValues values = new ContentValues();
        values.clear();
        values.put(Columns.PICK_COUNT, pickCount);
        resolver.insert(buildPickRecordUri(fileHashId), values);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported Uri " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final String hashId = uri.getPathSegments().get(1);
        return db.delete(TABLE_PICK_COUNT_RECORD, Columns.FILE_HASH_ID + "=?",
            new String[] { hashId });
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }
}