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

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.SmsManager;
import android.util.Log;

/**
 * IntentService whose purpose is to handle outgoing SMS intents for this application and schedule
 * them onto a AsyncTask to sleep for 5 seconds. This allows us to simulate SMS messages being sent
 * from background services.
 */
public class SmsManagerTestService extends IntentService {

    private static final String LOG_TAG = "smsmanagertestservice";

    private static class SendSmsJob extends AsyncTask<Intent, Void, Void> {

        @Override
        protected Void doInBackground(Intent... intents) {
            Intent intent = intents[0];
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // testing
            }

            String text = intent.getStringExtra(EXTRA_SEND_TEXT);
            String phoneNumber = intent.getStringExtra(EXTRA_SEND_NUMBER);
            PendingIntent sendIntent = intent.getParcelableExtra(EXTRA_SEND_INTENT);
            sendSms(phoneNumber, text, sendIntent);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.i(LOG_TAG, "SMS sent");
        }

    }

    public static final String SEND_SMS = "com.android.phone.testapps.smsmanagertestapp.send_sms";
    public static final String EXTRA_SEND_TEXT = "text";
    public static final String EXTRA_SEND_NUMBER = "number";
    public static final String EXTRA_SEND_INTENT = "sendIntent";

    public SmsManagerTestService() {
        super("SmsManagerTestService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case SEND_SMS : {
                new SendSmsJob().execute(intent);
                break;
            }
        }
    }

    private static void sendSms(String phoneNumber, String text, PendingIntent sendIntent) {
        SmsManager m = SmsManager.getDefault();
        m.sendTextMessage(phoneNumber, null, text, sendIntent, null);
    }
}
