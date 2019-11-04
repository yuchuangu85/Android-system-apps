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

package com.android.car.dialer.livedata;

import static com.android.car.dialer.livedata.CallHistoryLiveData.CallType.CALL_TYPE_ALL;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;

import androidx.annotation.IntDef;

import com.android.car.telephony.common.AsyncQueryLiveData;
import com.android.car.telephony.common.PhoneCallLog;
import com.android.car.telephony.common.QueryParam;

import java.util.ArrayList;
import java.util.List;

/**
 * Live data which loads call history.
 */
//TODO: Rename to PhoneCallLogLiveData
public class CallHistoryLiveData extends AsyncQueryLiveData<List<PhoneCallLog>> {
    /** The default limit of loading call logs */
    private final static int DEFAULT_CALL_LOG_LIMIT = 100;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    @IntDef({
            CALL_TYPE_ALL,
            CallType.INCOMING_TYPE,
            CallType.OUTGOING_TYPE,
            CallType.MISSED_TYPE,
    })
    public @interface CallType {
        int CALL_TYPE_ALL = -1;
        int INCOMING_TYPE = CallLog.Calls.INCOMING_TYPE;
        int OUTGOING_TYPE = CallLog.Calls.OUTGOING_TYPE;
        int MISSED_TYPE = CallLog.Calls.MISSED_TYPE;
        int VOICEMAIL_TYPE = CallLog.Calls.VOICEMAIL_TYPE;
    }

    /**
     * Creates a new instance of call history live data which loads all types of call history
     * with a limit of 100 logs.
     */
    public static CallHistoryLiveData newInstance(Context context) {
        return newInstance(context, CALL_TYPE_ALL, DEFAULT_CALL_LOG_LIMIT);
    }

    /**
     * Returns a new instance of last call live data.
     */
    public static CallHistoryLiveData newLastCallLiveData(Context context) {
        return newInstance(context, CALL_TYPE_ALL, 1);
    }

    private static CallHistoryLiveData newInstance(Context context, int callType, int limit) {
        StringBuilder where = new StringBuilder();
        List<String> selectionArgs = new ArrayList<>();
        limit = limit < 0 ? 0 : limit;

        if (callType != CALL_TYPE_ALL) {
            // add a filter for call type
            where.append(String.format("(%s = ?)", CallLog.Calls.TYPE));
            selectionArgs.add(Integer.toString(callType));
        }
        String selection = where.length() > 0 ? where.toString() : null;

        Uri uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY,
                        Integer.toString(limit))
                .build();
        QueryParam queryParam = new QueryParam(
                uri,
                null,
                selection,
                selectionArgs.toArray(EMPTY_STRING_ARRAY),
                CallLog.Calls.DEFAULT_SORT_ORDER);
        return new CallHistoryLiveData(context, queryParam);
    }

    private final Context mContext;
    private CallHistoryLiveData(Context context, QueryParam queryParam) {
        super(context, QueryParam.of(queryParam));
        mContext = context;
    }

    @Override
    protected List<PhoneCallLog> convertToEntity(Cursor cursor) {
        List<PhoneCallLog> resultList = new ArrayList<>();

        while (cursor.moveToNext()) {
            PhoneCallLog phoneCallLog = PhoneCallLog.fromCursor(mContext, cursor);
            PhoneCallLog previousCallLog = resultList.isEmpty() ? null : resultList.get(
                    resultList.size() - 1);

            if (previousCallLog == null || !previousCallLog.merge(phoneCallLog)) {
                resultList.add(phoneCallLog);
            }
        }
        return resultList;
    }
}
