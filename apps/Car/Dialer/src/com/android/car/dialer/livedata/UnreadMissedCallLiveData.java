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

package com.android.car.dialer.livedata;

import android.content.Context;
import android.database.Cursor;
import android.provider.CallLog;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.android.car.telephony.common.AsyncQueryLiveData;
import com.android.car.telephony.common.PhoneCallLog;
import com.android.car.telephony.common.QueryParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@link LiveData} for missed calls that haven't been read by user. */
public class UnreadMissedCallLiveData extends AsyncQueryLiveData<List<PhoneCallLog>> {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    /** Get the {@link UnreadMissedCallLiveData} instance. */
    public static UnreadMissedCallLiveData newInstance(Context context) {
        StringBuilder where = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        where.append(String.format("(%s = ?)", CallLog.Calls.TYPE));
        where.append(String.format("AND (%s = 1)", CallLog.Calls.NEW));
        where.append(String.format("AND (%s IS NOT 1)", CallLog.Calls.IS_READ));
        selectionArgs.add(Integer.toString(CallLog.Calls.MISSED_TYPE));

        String selection = where.length() > 0 ? where.toString() : null;

        QueryParam queryParam = new QueryParam(
                CallLog.Calls.CONTENT_URI,
                null,
                selection,
                selectionArgs.toArray(EMPTY_STRING_ARRAY),
                CallLog.Calls.DEFAULT_SORT_ORDER);
        return new UnreadMissedCallLiveData(context, queryParam);
    }

    private final Context mContext;

    private UnreadMissedCallLiveData(Context context, QueryParam queryParam) {
        super(context, QueryParam.of(queryParam));
        setValue(Collections.EMPTY_LIST);
        mContext = context;
    }

    @NonNull
    @Override
    protected List<PhoneCallLog> convertToEntity(@NonNull Cursor cursor) {
        List<PhoneCallLog> missedCalls = new ArrayList<>();

        while (cursor.moveToNext()) {
            PhoneCallLog phoneCallLog = PhoneCallLog.fromCursor(mContext, cursor);
            int index = missedCalls.indexOf(phoneCallLog);
            PhoneCallLog existingCallLog = null;
            if (index != -1) {
                existingCallLog = missedCalls.get(index);
            }

            if (existingCallLog == null || !existingCallLog.merge(phoneCallLog)) {
                missedCalls.add(phoneCallLog);
            }
        }
        return missedCalls;
    }
}
