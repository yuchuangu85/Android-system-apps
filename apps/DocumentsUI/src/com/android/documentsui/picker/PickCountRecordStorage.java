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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public interface PickCountRecordStorage {
    int getPickCountRecord(Context context, Uri uri);
    void setPickCountRecord(Context context, Uri uri, int pickCount);
    int increasePickCountRecord(Context context, Uri uri);

    static PickCountRecordStorage create() {
        return new PickCountRecordStorage() {
            private static final String TAG = "PickCountRecordStorage";

            @Override
            public int getPickCountRecord(Context context, Uri uri) {
                int fileHashId = uri.hashCode();
                Uri pickRecordUri = PickCountRecordProvider.buildPickRecordUri(fileHashId);
                final ContentResolver resolver = context.getContentResolver();
                int count = 0;
                try (Cursor cursor = resolver.query(pickRecordUri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        final int index = cursor
                            .getColumnIndex(PickCountRecordProvider.Columns.PICK_COUNT);
                        if (index != -1) {
                            count = cursor.getInt(index);
                        }
                    }
                }
                return count;
            }

            @Override
            public void setPickCountRecord(Context context, Uri uri, int pickCount) {
                PickCountRecordProvider.setPickRecord(
                    context.getContentResolver(), uri.hashCode(), pickCount);
            }

            @Override
            public int increasePickCountRecord(Context context, Uri uri) {
                int pickCount = getPickCountRecord(context, uri) + 1;
                setPickCountRecord(context, uri, pickCount);
                return pickCount;
            }
        };
    }
}
