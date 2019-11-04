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

package com.android.car.dialer.notification;

import static android.telecom.TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION;
import static android.telecom.TelecomManager.EXTRA_NOTIFICATION_COUNT;
import static android.telecom.TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Log;

import com.android.car.dialer.log.L;

/**
 * A {@link BroadcastReceiver} that is used to inform telecom manager that we are showing the
 * missed call notification that it does not have to show missed call notification on its behalf.
 *
 * <p>We only log the intent. The missed call notification is monitored and handled in the {@link
 * MissedCallNotificationController}.
 */
public class MissedCallReceiver extends BroadcastReceiver {
    private static final String TAG = "CD.MissedCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(action)) {
            return;
        }

        int count = intent.getIntExtra(EXTRA_NOTIFICATION_COUNT, 0);
        String phoneNumber = intent.getStringExtra(EXTRA_NOTIFICATION_PHONE_NUMBER);

        L.d(TAG, "Count: %d PhoneNumber: %s", count, Log.pii(phoneNumber));
    }
}
