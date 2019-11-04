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

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import com.android.documentsui.Metrics;

// load & update mime type & repeatedly pick count in background
public class UpdatePickResultTask extends AsyncTask<Void, Void, Void> {
    private Context mContext;
    private PickResult mPickResult;
    private PickCountRecordStorage mPickCountRecord;

    public UpdatePickResultTask(Context context, PickResult pickResult) {
        this(context, pickResult, PickCountRecordStorage.create());
    }

    public UpdatePickResultTask(Context context, PickResult pickResult,
        PickCountRecordStorage pickCountRecord) {
        mContext = context.getApplicationContext();
        mPickResult = pickResult;
        mPickCountRecord = pickCountRecord;
    }

    @Override
    protected void onPreExecute() {
        mPickResult.increaseDuration(SystemClock.uptimeMillis());
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Uri fileUri = mPickResult.getFileUri();
        if (fileUri != null) {
            mPickResult.setMimeType(
                Metrics.sanitizeMime(mContext.getContentResolver().getType(fileUri)));

            mPickResult.setRepeatedPickTimes(
                mPickCountRecord.increasePickCountRecord(mContext, fileUri));
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        Metrics.logPickResult(mPickResult);
    }

}
