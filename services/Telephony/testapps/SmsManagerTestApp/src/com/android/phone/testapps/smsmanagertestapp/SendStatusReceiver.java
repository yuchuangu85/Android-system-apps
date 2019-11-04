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

package com.android.phone.testapps.smsmanagertestapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Handles the PendingIntent result from SMS messages send to Telephony. Reports the results of
 * those messages using Toasts.
 */
public class SendStatusReceiver extends BroadcastReceiver {

    public static final String MESSAGE_SENT_ACTION =
            "com.android.phone.testapps.smsmanagertestapp.message_sent_action";

    // Defined by platform, but no constant provided. See docs for SmsManager.sendTextMessage.
    private static final String EXTRA_ERROR_CODE = "errorCode";
    private static final String EXTRA_NO_DEFAULT = "noDefault";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int resultCode = getResultCode();
        if (MESSAGE_SENT_ACTION.equals(intent.getAction())) {
            int errorCode = intent.getIntExtra(EXTRA_ERROR_CODE, -1);
            boolean userCancel = intent.getBooleanExtra(EXTRA_NO_DEFAULT, false);
            if (userCancel) {
                Toast.makeText(context, "SMS not sent, user cancelled.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "SMS result=" + resultCode + ", error extra=" + errorCode,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
