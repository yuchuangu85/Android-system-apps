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
package com.google.android.car.bugreport;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Provides a bug storage interface to save and upload bugreports filed from all users.
 * In Android Automotive user 0 runs as the system and all the time, while other users won't once
 * their session ends. This content provider enables bug reports to be uploaded even after
 * user session ends.
 */
public class BugStorageProvider extends ContentProvider {
    private static final String TAG = BugStorageProvider.class.getSimpleName();

    private static final String AUTHORITY = "com.google.android.car.bugreport";
    private static final String BUG_REPORTS_TABLE = "bugreports";
    static final Uri BUGREPORT_CONTENT_URI =
            Uri.parse("content://" + AUTHORITY + "/" + BUG_REPORTS_TABLE);

    static final String COLUMN_ID = "_ID";
    static final String COLUMN_USERNAME = "username";
    static final String COLUMN_TITLE = "title";
    static final String COLUMN_TIMESTAMP = "timestamp";
    static final String COLUMN_DESCRIPTION = "description";
    static final String COLUMN_FILEPATH = "filepath";
    static final String COLUMN_STATUS = "status";
    static final String COLUMN_STATUS_MESSAGE = "message";

    // URL Matcher IDs.
    private static final int URL_MATCHED_BUG_REPORTS_URI = 1;
    private static final int URL_MATCHED_BUG_REPORT_ID_URI = 2;

    private Handler mHandler;

    private DatabaseHelper mDatabaseHelper;
    private final UriMatcher mUriMatcher;

    /**
     * A helper class to work with sqlite database.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String TAG = DatabaseHelper.class.getSimpleName();

        private static final String DATABASE_NAME = "bugreport.db";

        /**
         * All changes in database versions should be recorded here.
         * 1: Initial version.
         */
        private static final int INITIAL_VERSION = 1;
        private static final int DATABASE_VERSION = INITIAL_VERSION;

        private static final String CREATE_TABLE = "CREATE TABLE " + BUG_REPORTS_TABLE + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY,"
                + COLUMN_USERNAME + " TEXT,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_TIMESTAMP + " TEXT NOT NULL,"
                + COLUMN_DESCRIPTION + " TEXT NULL,"
                + COLUMN_FILEPATH + " TEXT DEFAULT NULL,"
                + COLUMN_STATUS + " INTEGER DEFAULT " + Status.STATUS_WRITE_PENDING.getValue() + ","
                + COLUMN_STATUS_MESSAGE + " TEXT NULL"
                + ");";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading from " + oldVersion + " to " + newVersion);
        }
    }

    /** Builds {@link Uri} that points to a bugreport entry with provided bugreport id. */
    static Uri buildUriWithBugId(int bugReportId) {
        return Uri.parse("content://" + AUTHORITY + "/" + BUG_REPORTS_TABLE + "/" + bugReportId);
    }

    public BugStorageProvider() {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(AUTHORITY, BUG_REPORTS_TABLE, URL_MATCHED_BUG_REPORTS_URI);
        mUriMatcher.addURI(AUTHORITY, BUG_REPORTS_TABLE + "/#", URL_MATCHED_BUG_REPORT_ID_URI);
    }

    @Override
    public boolean onCreate() {
        mDatabaseHelper = new DatabaseHelper(getContext());
        mHandler = new Handler();
        return true;
    }

    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder,
            @Nullable CancellationSignal cancellationSignal) {
        String table;
        switch (mUriMatcher.match(uri)) {
            // returns the list of bugreports that match the selection criteria.
            case URL_MATCHED_BUG_REPORTS_URI:
                table = BUG_REPORTS_TABLE;
                break;
            //  returns the bugreport that match the id.
            case URL_MATCHED_BUG_REPORT_ID_URI:
                table = BUG_REPORTS_TABLE;
                if (selection != null || selectionArgs != null) {
                    throw new IllegalArgumentException("selection is not allowed for "
                            + URL_MATCHED_BUG_REPORT_ID_URI);
                }
                selection = COLUMN_ID + "=?";
                selectionArgs = new String[]{ uri.getLastPathSegment() };
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
        SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
        Cursor cursor = db.query(false, table, null, selection, selectionArgs, null, null,
                sortOrder, null, cancellationSignal);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        String table;
        if (values == null) {
            throw new IllegalArgumentException("values cannot be null");
        }
        switch (mUriMatcher.match(uri)) {
            case URL_MATCHED_BUG_REPORTS_URI:
                table = BUG_REPORTS_TABLE;
                String filepath = FileUtils.getZipFile(getContext(),
                        (String) values.get(COLUMN_TIMESTAMP),
                        (String) values.get(COLUMN_USERNAME)).getPath();
                values.put(COLUMN_FILEPATH, filepath);
                break;
            default:
                throw new IllegalArgumentException("unknown uri" + uri);
        }
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        long rowId = db.insert(table, null, values);
        if (rowId > 0) {
            Uri resultUri = Uri.parse("content://" + AUTHORITY + "/" + table + "/" + rowId);
            // notify registered content observers
            getContext().getContentResolver().notifyChange(resultUri, null);
            return resultUri;
        }
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (mUriMatcher.match(uri) != URL_MATCHED_BUG_REPORT_ID_URI) {
            throw new IllegalArgumentException("unknown uri:" + uri);
        }
        // We only store zip files in this provider.
        return "application/zip";
    }

    @Override
    public int delete(
            @NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        switch (mUriMatcher.match(uri)) {
            //  returns the bugreport that match the id.
            case URL_MATCHED_BUG_REPORT_ID_URI:
                if (selection != null || selectionArgs != null) {
                    throw new IllegalArgumentException("selection is not allowed for "
                            + URL_MATCHED_BUG_REPORT_ID_URI);
                }
                selection = COLUMN_ID + " = ?";
                selectionArgs = new String[]{uri.getLastPathSegment()};
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
        SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
        return db.delete(BUG_REPORTS_TABLE, selection, selectionArgs);
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs) {
        if (values == null) {
            throw new IllegalArgumentException("values cannot be null");
        }
        String table;
        switch (mUriMatcher.match(uri)) {
            case URL_MATCHED_BUG_REPORTS_URI:
                table = BUG_REPORTS_TABLE;
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri);
        }
        SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
        int rowCount = db.update(table, values, selection, selectionArgs);
        if (rowCount > 0) {
            // notify registered content observers
            getContext().getContentResolver().notifyChange(uri, null);
        }
        Integer status = values.getAsInteger(COLUMN_STATUS);
        // When the status is set to STATUS_UPLOAD_PENDING, we schedule an UploadJob under the
        // current user, which is the primary user.
        if (status != null && status.equals(Status.STATUS_UPLOAD_PENDING.getValue())) {
            JobSchedulingUtils.scheduleUploadJob(BugStorageProvider.this.getContext());
        }
        return rowCount;
    }

    /**
     * This is called when the OutputStream is requested by
     * {@link BugStorageUtils#openBugReportFile}.
     *
     * It expects the file to be a zip file and schedules an upload under the primary user.
     */
    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
            throws FileNotFoundException {
        if (mUriMatcher.match(uri) != URL_MATCHED_BUG_REPORT_ID_URI) {
            throw new IllegalArgumentException("unknown uri:" + uri);
        }

        Cursor c = query(uri, new String[]{COLUMN_FILEPATH}, null, null, null);
        int count = (c != null) ? c.getCount() : 0;
        if (count != 1) {
            // If there is not exactly one result, throw an appropriate
            // exception.
            if (c != null) {
                c.close();
            }
            if (count == 0) {
                throw new FileNotFoundException("No entry for " + uri);
            }
            throw new FileNotFoundException("Multiple items at " + uri);
        }

        c.moveToFirst();
        int i = c.getColumnIndex(COLUMN_FILEPATH);
        String path = (i >= 0 ? c.getString(i) : null);
        c.close();
        if (path == null) {
            throw new FileNotFoundException("Column for path not found.");
        }

        int modeBits = ParcelFileDescriptor.parseMode(mode);
        try {
            return ParcelFileDescriptor.open(new File(path), modeBits, mHandler, e -> {
                if (mode.equals("r")) {
                    Log.i(TAG, "File " + path + " opened in read-only mode.");
                    return;
                } else if (!mode.equals("w")) {
                    Log.e(TAG, "Only read-only or write-only mode supported; mode=" + mode);
                    return;
                }
                Log.i(TAG, "File " + path + " opened in write-only mode.");
                Status status;
                if (e == null) {
                    // success writing the file. Update the field to indicate bugreport
                    // is ready for upload
                    status = JobSchedulingUtils.uploadByDefault() ? Status.STATUS_UPLOAD_PENDING
                            : Status.STATUS_PENDING_USER_ACTION;
                } else {
                    // We log it and ignore it
                    Log.e(TAG, "Bug report file write failed ", e);
                    status = Status.STATUS_WRITE_FAILED;
                }
                SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put(COLUMN_STATUS, status.getValue());
                db.update(BUG_REPORTS_TABLE, values, COLUMN_ID + "=?",
                        new String[]{ uri.getLastPathSegment() });
                if (status == Status.STATUS_UPLOAD_PENDING) {
                    JobSchedulingUtils.scheduleUploadJob(BugStorageProvider.this.getContext());
                }
                Log.i(TAG, "Finished adding bugreport " + path + " " + uri);
            });
        } catch (IOException e) {
            // An IOException (for example not being able to open the file, will crash us.
            // That is ok.
            throw new RuntimeException(e);
        }
    }
}
