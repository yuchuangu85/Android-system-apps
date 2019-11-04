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

package com.android.car.telephony.common;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Represents query parameters. */
public class QueryParam {

    /** Creates a provider always returning the same query param instance. */
    public static Provider of(final QueryParam queryParam) {
        return () -> queryParam;
    }

    /**
     * An object capable of providing instances of {@link QueryParam}. It can be used in two
     * circumstances:
     * <ul>
     * <li>Return the same instance every time calling the getter.
     * <li>Return an updated instance or create a new instance every time calling the getter.
     */
    public interface Provider {

        /** Returns an instance of query params. */
        @Nullable
        QueryParam getQueryParam();
    }

    /** Used by {@link ObservableAsyncQuery#startQuery()} as query param. */
    final Uri mUri;
    /** Used by {@link ObservableAsyncQuery#startQuery()} as query param. */
    final String[] mProjection;
    /** Used by {@link ObservableAsyncQuery#startQuery()} as query param. */
    final String mSelection;
    /** Used by {@link ObservableAsyncQuery#startQuery()} as query param. */
    final String[] mSelectionArgs;
    /** Used by {@link ObservableAsyncQuery#startQuery()} as query param. */
    final String mOrderBy;

    public QueryParam(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String orderBy) {
        mUri = uri;
        mProjection = projection;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mOrderBy = orderBy;
    }
}
