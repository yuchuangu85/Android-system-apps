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

import android.app.PendingIntent;

/**
 * Interface for classes which thinly wrap {@link android.app.AlarmManager}.
 */
public interface AlarmManagerWrapper {

    /**
     * See {@link android.app.AlarmManager#set(int, long, PendingIntent)}.
     */
    void set(int type, long triggerAtMillis, PendingIntent operation);

    /**
     * See {@link android.app.AlarmManager#setWindow(int, long, long, PendingIntent)}.
     */
    void setWindow(int type, long windowStartMillis, long windowLengthMillis,
                   PendingIntent operation);

    /**
     * See {@link android.app.AlarmManager#setInexactRepeating(int, long, long, PendingIntent)}.
     */
    void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis,
                             PendingIntent operation);

    /**
     * See {@link android.app.AlarmManager#cancel(PendingIntent)}.
     */
    void cancel(PendingIntent operation);
}
