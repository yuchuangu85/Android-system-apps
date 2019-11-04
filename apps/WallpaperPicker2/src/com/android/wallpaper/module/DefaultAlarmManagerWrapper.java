/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;

/**
 * Default implementation of {@link AlarmManagerWrapper}.
 */
public class DefaultAlarmManagerWrapper implements AlarmManagerWrapper {

    private AlarmManager mAlarmManager;

    public DefaultAlarmManagerWrapper(Context context) {
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        mAlarmManager.set(type, triggerAtMillis, operation);
    }

    @Override
    public void setWindow(int type, long windowStartMillis, long windowLengthMillis,
                          PendingIntent operation) {
        mAlarmManager.setWindow(type, windowStartMillis, windowLengthMillis, operation);
    }

    @Override
    public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis,
                                    PendingIntent operation) {
        mAlarmManager.setInexactRepeating(type, triggerAtMillis, intervalMillis, operation);
    }

    @Override
    public void cancel(PendingIntent operation) {
        mAlarmManager.cancel(operation);
    }
}
