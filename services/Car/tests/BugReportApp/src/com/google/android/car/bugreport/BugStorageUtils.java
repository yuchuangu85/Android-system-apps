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

import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_FILEPATH;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_ID;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_STATUS;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_STATUS_MESSAGE;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_TIMESTAMP;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_TITLE;
import static com.google.android.car.bugreport.BugStorageProvider.COLUMN_USERNAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.auth.oauth2.TokenResponseException;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A class that hides details when communicating with the bug storage provider.
 */
final class BugStorageUtils {
    private static final String TAG = BugStorageUtils.class.getSimpleName();

    /**
     * When time/time-zone set incorrectly, Google API returns "400: invalid_grant" error with
     * description containing this text.
     */
    private static final String CLOCK_SKEW_ERROR = "clock with skew to account";

    /** When time/time-zone set incorrectly, Google API returns this error. */
    private static final String INVALID_GRANT = "invalid_grant";

    private static final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    /**
     * Creates a new {@link Status#STATUS_WRITE_PENDING} bug report record in a local sqlite
     * database.
     *
     * @param context   - an application context.
     * @param title     - title of the bug report.
     * @param timestamp - timestamp when the bug report was initiated.
     * @param username  - current user name. Note, it's a user name, not an account name.
     * @return an instance of {@link MetaBugReport} that was created in a database.
     */
    @NonNull
    static MetaBugReport createBugReport(
            @NonNull Context context,
            @NonNull String title,
            @NonNull String timestamp,
            @NonNull String username) {
        // insert bug report username and title
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, title);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_USERNAME, username);

        ContentResolver r = context.getContentResolver();
        Uri uri = r.insert(BugStorageProvider.BUGREPORT_CONTENT_URI, values);

        Cursor c = r.query(uri, new String[]{COLUMN_ID}, null, null, null);
        int count = (c == null) ? 0 : c.getCount();
        if (count != 1) {
            throw new RuntimeException("Could not create a bug report entry.");
        }
        c.moveToFirst();
        int id = getInt(c, COLUMN_ID);
        c.close();
        return new MetaBugReport.Builder(id, timestamp)
                .setTitle(title)
                .setUserName(username)
                .build();
    }

    /**
     * Returns a file stream to write the zipped file to. The content provider listens for file
     * descriptor to be closed, and as soon as it is closed, {@link BugStorageProvider} schedules
     * it for upload.
     *
     * @param context       - an application context.
     * @param metaBugReport - a bug report.
     * @return a file descriptor where a zip content should be written.
     */
    @NonNull
    static OutputStream openBugReportFile(
            @NonNull Context context, @NonNull MetaBugReport metaBugReport)
            throws FileNotFoundException {
        ContentResolver r = context.getContentResolver();

        // Write the file. When file is closed, bug report record status
        // will automatically be made ready for uploading.
        return r.openOutputStream(BugStorageProvider.buildUriWithBugId(metaBugReport.getId()));
    }

    /**
     * Deletes {@link MetaBugReport} record from a local database. Returns true if the record was
     * deleted.
     *
     * @param context     - an application context.
     * @param bugReportId - a bug report id.
     * @return true if the record was deleted.
     */
    static boolean deleteBugReport(@NonNull Context context, int bugReportId) {
        ContentResolver r = context.getContentResolver();
        return r.delete(BugStorageProvider.buildUriWithBugId(bugReportId), null, null) == 1;
    }

    /**
     * Returns bugreports that are waiting to be uploaded.
     */
    @NonNull
    public static List<MetaBugReport> getPendingBugReports(@NonNull Context context) {
        String selection = COLUMN_STATUS + "=?";
        String[] selectionArgs = new String[]{
                Integer.toString(Status.STATUS_UPLOAD_PENDING.getValue())};
        return getBugreports(context, selection, selectionArgs, null);
    }

    /**
     * Returns all bugreports in descending order by the ID field. ID is the index in the
     * database.
     */
    @NonNull
    public static List<MetaBugReport> getAllBugReportsDescending(@NonNull Context context) {
        return getBugreports(context, null, null, COLUMN_ID + " DESC");
    }

    private static List<MetaBugReport> getBugreports(Context context, String selection,
            String[] selectionArgs, String order) {
        ArrayList<MetaBugReport> bugReports = new ArrayList<>();
        String[] projection = {
                COLUMN_ID,
                COLUMN_USERNAME,
                COLUMN_TITLE,
                COLUMN_TIMESTAMP,
                COLUMN_FILEPATH,
                COLUMN_STATUS,
                COLUMN_STATUS_MESSAGE};
        ContentResolver r = context.getContentResolver();
        Cursor c = r.query(BugStorageProvider.BUGREPORT_CONTENT_URI, projection,
                selection, selectionArgs, order);

        int count = (c != null) ? c.getCount() : 0;

        if (count > 0) c.moveToFirst();
        for (int i = 0; i < count; i++) {
            MetaBugReport meta = new MetaBugReport.Builder(getInt(c, COLUMN_ID),
                    getString(c, COLUMN_TIMESTAMP))
                    .setUserName(getString(c, COLUMN_USERNAME))
                    .setTitle(getString(c, COLUMN_TITLE))
                    .setFilepath(getString(c, COLUMN_FILEPATH))
                    .setStatus(getInt(c, COLUMN_STATUS))
                    .setStatusMessage(getString(c, COLUMN_STATUS_MESSAGE))
                    .build();
            bugReports.add(meta);
            c.moveToNext();
        }
        if (c != null) c.close();
        return bugReports;
    }

    /**
     * returns 0 if the column is not found. Otherwise returns the column value.
     */
    private static int getInt(Cursor c, String colName) {
        int colIndex = c.getColumnIndex(colName);
        if (colIndex == -1) {
            Log.w(TAG, "Column " + colName + " not found.");
            return 0;
        }
        return c.getInt(colIndex);
    }

    /**
     * Returns the column value. If the column is not found returns empty string.
     */
    private static String getString(Cursor c, String colName) {
        int colIndex = c.getColumnIndex(colName);
        if (colIndex == -1) {
            Log.w(TAG, "Column " + colName + " not found.");
            return "";
        }
        return c.getString(colIndex);
    }

    /**
     * Sets bugreport status to uploaded successfully.
     */
    public static void setUploadSuccess(Context context, MetaBugReport bugReport) {
        setBugReportStatus(context, bugReport, Status.STATUS_UPLOAD_SUCCESS,
                "Upload time: " + currentTimestamp());
    }

    /**
     * Sets bugreport status to upload failed.
     */
    public static void setUploadFailed(Context context, MetaBugReport bugReport, Exception e) {
        setBugReportStatus(context, bugReport, Status.STATUS_UPLOAD_FAILED, getRootCauseMessage(e));
    }

    /**
     * Sets bugreport status pending, and update the message to last exception message.
     *
     * <p>Used when a transient error has occurred.
     */
    public static void setUploadRetry(Context context, MetaBugReport bugReport, Exception e) {
        setBugReportStatus(context, bugReport, Status.STATUS_UPLOAD_PENDING,
                getRootCauseMessage(e));
    }

    /**
     * Sets bugreport status pending and update the message to last message.
     *
     * <p>Used when a transient error has occurred.
     */
    public static void setUploadRetry(Context context, MetaBugReport bugReport, String msg) {
        setBugReportStatus(context, bugReport, Status.STATUS_UPLOAD_PENDING, msg);
    }

    /** Gets the root cause of the error. */
    @NonNull
    private static String getRootCauseMessage(@Nullable Throwable t) {
        if (t == null) {
            return "No error";
        } else if (t instanceof TokenResponseException) {
            TokenResponseException ex = (TokenResponseException) t;
            if (ex.getDetails().getError().equals(INVALID_GRANT)
                    && ex.getDetails().getErrorDescription().contains(CLOCK_SKEW_ERROR)) {
                return "Auth error. Check if time & time-zone is correct.";
            }
        }
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }

    /** Updates bug report record status. */
    static void setBugReportStatus(
            Context context, MetaBugReport bugReport, Status status, String message) {
        // update status
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status.getValue());
        if (!TextUtils.isEmpty(message)) {
            values.put(COLUMN_STATUS_MESSAGE, message);
        }
        String where = COLUMN_ID + "=" + bugReport.getId();
        context.getContentResolver().update(BugStorageProvider.BUGREPORT_CONTENT_URI, values,
                where, null);
    }

    private static String currentTimestamp() {
        return TIMESTAMP_FORMAT.format(new Date());
    }
}
