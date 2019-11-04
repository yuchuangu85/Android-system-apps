/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static java.lang.Boolean.TRUE;

import android.content.Context;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.StringDef;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Log;
import com.android.tv.data.BaseProgram;
import com.android.tv.features.PartnerFeatures;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A utility class related to TvProvider. */
public final class TvProviderUtils {
    private static final String TAG = "TvProviderUtils";

    public static final String EXTRA_PROGRAM_COLUMN_SERIES_ID = BaseProgram.COLUMN_SERIES_ID;
    public static final String EXTRA_PROGRAM_COLUMN_STATE = BaseProgram.COLUMN_STATE;

    /** Possible extra columns in TV provider. */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({EXTRA_PROGRAM_COLUMN_SERIES_ID, EXTRA_PROGRAM_COLUMN_STATE})
    public @interface TvProviderExtraColumn {}

    private static boolean sProgramHasSeriesIdColumn;
    private static boolean sRecordedProgramHasSeriesIdColumn;
    private static boolean sRecordedProgramHasStateColumn;

    /**
     * Checks whether a table contains a series ID column.
     *
     * <p>This method is different from {@link #getProgramHasSeriesIdColumn()} and {@link
     * #getRecordedProgramHasSeriesIdColumn()} because it may access to database, so it should be
     * run in worker thread.
     *
     * @return {@code true} if the corresponding table contains a series ID column; {@code false}
     *     otherwise.
     */
    @WorkerThread
    public static synchronized boolean checkSeriesIdColumn(Context context, Uri uri) {
        boolean canCreateColumn = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        if (!canCreateColumn) {
            return false;
        }
        return (Utils.isRecordedProgramsUri(uri)
                        && checkRecordedProgramTableSeriesIdColumn(context, uri))
                || (Utils.isProgramsUri(uri) && checkProgramTableSeriesIdColumn(context, uri));
    }

    @WorkerThread
    private static synchronized boolean checkProgramTableSeriesIdColumn(Context context, Uri uri) {
        if (!sProgramHasSeriesIdColumn) {
            if (getExistingColumns(context, uri).contains(EXTRA_PROGRAM_COLUMN_SERIES_ID)) {
                sProgramHasSeriesIdColumn = true;
            } else if (addColumnToTable(context, uri, EXTRA_PROGRAM_COLUMN_SERIES_ID)) {
                sProgramHasSeriesIdColumn = true;
            }
        }
        return sProgramHasSeriesIdColumn;
    }

    @WorkerThread
    private static synchronized boolean checkRecordedProgramTableSeriesIdColumn(
            Context context, Uri uri) {
        if (!sRecordedProgramHasSeriesIdColumn) {
            if (getExistingColumns(context, uri).contains(EXTRA_PROGRAM_COLUMN_SERIES_ID)) {
                sRecordedProgramHasSeriesIdColumn = true;
            } else if (addColumnToTable(context, uri, EXTRA_PROGRAM_COLUMN_SERIES_ID)) {
                sRecordedProgramHasSeriesIdColumn = true;
            }
        }
        return sRecordedProgramHasSeriesIdColumn;
    }

    /**
     * Checks whether a table contains a state column.
     *
     * <p>This method is different from {@link #getRecordedProgramHasStateColumn()} because it may
     * access to database, so it should be run in worker thread.
     *
     * @return {@code true} if the corresponding table contains a state column; {@code false}
     *     otherwise.
     */
    @WorkerThread
    public static synchronized boolean checkStateColumn(Context context, Uri uri) {
        boolean canCreateColumn = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
        if (!canCreateColumn) {
            return false;
        }
        return (Utils.isRecordedProgramsUri(uri)
                && checkRecordedProgramTableStateColumn(context, uri));
    }

    @WorkerThread
    private static synchronized boolean checkRecordedProgramTableStateColumn(
            Context context, Uri uri) {
        if (!sRecordedProgramHasStateColumn) {
            if (getExistingColumns(context, uri).contains(EXTRA_PROGRAM_COLUMN_STATE)) {
                sRecordedProgramHasStateColumn = true;
            } else if (addColumnToTable(context, uri, EXTRA_PROGRAM_COLUMN_STATE)) {
                sRecordedProgramHasStateColumn = true;
            }
        }
        return sRecordedProgramHasStateColumn;
    }

    public static synchronized boolean getProgramHasSeriesIdColumn() {
        return TRUE.equals(sProgramHasSeriesIdColumn);
    }

    public static synchronized boolean getRecordedProgramHasSeriesIdColumn() {
        return TRUE.equals(sRecordedProgramHasSeriesIdColumn);
    }

    public static synchronized boolean getRecordedProgramHasStateColumn() {
        return TRUE.equals(sRecordedProgramHasStateColumn);
    }

    public static String[] addExtraColumnsToProjection(String[] projection,
            @TvProviderExtraColumn String column) {
        List<String> projectionList = new ArrayList<>(Arrays.asList(projection));
        if (!projectionList.contains(column)) {
            projectionList.add(column);
        }
        projection = projectionList.toArray(projection);
        return projection;
    }

    /**
     * Gets column names of a table
     *
     * @param uri the corresponding URI of the table
     */
    @VisibleForTesting
    static Set<String> getExistingColumns(Context context, Uri uri) {
        Bundle result = null;
        try {
            result =
                    context.getContentResolver()
                            .call(uri, TvContract.METHOD_GET_COLUMNS, uri.toString(), null);
        } catch (Exception e) {
            Log.e(TAG, "Error trying to get existing columns.", e);
        }
        if (result != null) {
            String[] columns = result.getStringArray(TvContract.EXTRA_EXISTING_COLUMN_NAMES);
            if (columns != null) {
                return new HashSet<>(Arrays.asList(columns));
            }
        }
        Log.e(TAG, "Query existing column names from " + uri + " returned null");
        return Collections.emptySet();
    }

    /**
     * Add a column to the table
     *
     * @return {@code true} if the column is added successfully; {@code false} otherwise.
     */
    private static boolean addColumnToTable(Context context, Uri contentUri, String columnName) {
        Bundle extra = new Bundle();
        extra.putCharSequence(TvContract.EXTRA_COLUMN_NAME, columnName);
        extra.putCharSequence(TvContract.EXTRA_DATA_TYPE, "TEXT");
        // If the add operation fails, the following just returns null without crashing.
        Bundle allColumns = null;
        try {
            allColumns =
                    context.getContentResolver()
                            .call(
                                    contentUri,
                                    TvContract.METHOD_ADD_COLUMN,
                                    contentUri.toString(),
                                    extra);
        } catch (Exception e) {
            Log.e(TAG, "Error trying to add column.", e);
        }
        if (allColumns == null) {
            Log.w(TAG, "Adding new column failed. Uri=" + contentUri);
        }
        return allColumns != null;
    }

    private TvProviderUtils() {}
}
