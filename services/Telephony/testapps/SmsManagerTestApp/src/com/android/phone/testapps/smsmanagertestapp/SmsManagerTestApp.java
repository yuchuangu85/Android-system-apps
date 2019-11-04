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

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Supports sending an SMS immediately and offloading the sending of the SMS to a background task.
 */
public class SmsManagerTestApp extends Activity {

    private static final int REQUEST_PERMISSION_READ_STATE = 1;
    private static final int REQUEST_GET_SMS_SUB_ID = 2;

    private static final ComponentName SETTINGS_SUB_PICK_ACTIVITY = new ComponentName(
            "com.android.settings", "com.android.settings.sim.SimDialogActivity");

    /*
     * Forwarded constants from SimDialogActivity.
     */
    private static final String DIALOG_TYPE_KEY = "dialog_type";
    public static final String RESULT_SUB_ID = "result_sub_id";
    private static final int SMS_PICK = 2;

    private static int sMessageId = 0;
    private boolean mIsReadPhoneStateGranted = false;

    private EditText mPhoneNumber;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        findViewById(R.id.send_text_button).setOnClickListener(this::sendOutgoingSms);
        findViewById(R.id.send_text_button_service)
                .setOnClickListener(this::sendOutgoingSmsService);
        findViewById(R.id.get_sub_for_result_button).setOnClickListener(this::getSubIdForResult);
        mPhoneNumber = (EditText) findViewById(R.id.phone_number_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.SEND_SMS)
                        != PackageManager.PERMISSION_GRANTED) {
            mIsReadPhoneStateGranted = false;
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.SEND_SMS}, REQUEST_PERMISSION_READ_STATE);
        } else {
            mIsReadPhoneStateGranted = true;
        }
        if (mIsReadPhoneStateGranted) {
            mPhoneNumber.setText(getPhoneNumber(), TextView.BufferType.NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopService(new Intent(this, SmsManagerTestService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_READ_STATE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mIsReadPhoneStateGranted = true;
                } else {
                    // permission denied
                    Toast.makeText(this, "read_phone_state denied.", Toast.LENGTH_SHORT).show();
                }
            }

        }

        if (mIsReadPhoneStateGranted) {
            mPhoneNumber.setText(getPhoneNumber(), TextView.BufferType.NORMAL);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case (REQUEST_GET_SMS_SUB_ID) : {
                int resultSubId;
                if (resultCode == RESULT_OK) {
                    resultSubId = data == null ? -1 : data.getIntExtra(RESULT_SUB_ID,
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                    Toast.makeText(this, "User picked sub id = " + resultSubId,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "User cancelled dialog.",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }


    private void sendOutgoingSms(View view) {
        String phoneNumber = mPhoneNumber.getText().toString();
        if (TextUtils.isEmpty(phoneNumber)) {
            Toast.makeText(this, "Couldn't get phone number from view! Ignoring request...",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsReadPhoneStateGranted) {
            SmsManager m = SmsManager.getDefault();
            m.sendTextMessage(phoneNumber, null, "Test",
                    PendingIntent.getBroadcast(this, sMessageId, getSendStatusIntent(), 0),
                    null);
            sMessageId++;
        }
    }

    private void sendOutgoingSmsService(View view) {
        String phoneNumber = mPhoneNumber.getText().toString();
        if (TextUtils.isEmpty(phoneNumber)) {
            Toast.makeText(this, "Couldn't get phone number from view! Ignoring request...",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsReadPhoneStateGranted) {
            Intent sendSmsIntent = new Intent(SmsManagerTestService.SEND_SMS);
            sendSmsIntent.putExtra(SmsManagerTestService.EXTRA_SEND_TEXT, "Text");
            sendSmsIntent.putExtra(SmsManagerTestService.EXTRA_SEND_NUMBER, phoneNumber);
            sendSmsIntent.putExtra(SmsManagerTestService.EXTRA_SEND_INTENT,
                    PendingIntent.getBroadcast(this, sMessageId, getSendStatusIntent(), 0));
            sendSmsIntent.setComponent(new ComponentName(this, SmsManagerTestService.class));
            startService(sendSmsIntent);
            sMessageId++;
        }
    }
    private void getSubIdForResult(View view) {
        // ask the user for a default SMS SIM.
        Intent intent = new Intent();
        intent.setComponent(SETTINGS_SUB_PICK_ACTIVITY);
        intent.putExtra(DIALOG_TYPE_KEY, SMS_PICK);
        try {
            startActivity(intent, null);
        } catch (ActivityNotFoundException anfe) {
            // If Settings is not installed, only log the error as we do not want to break
            // legacy applications.
            Toast.makeText(this, "Unable to launch Settings application.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Intent getSendStatusIntent() {
        // Encode requestId in intent data
        return new Intent(SendStatusReceiver.MESSAGE_SENT_ACTION, null, this,
                SendStatusReceiver.class);
    }

    private String getPhoneNumber() {
        String result = "6505551212";
        int defaultSmsSub = SubscriptionManager.getDefaultSmsSubscriptionId();
        if (mIsReadPhoneStateGranted) {
            TelephonyManager tm = getSystemService(TelephonyManager.class);
            if (tm != null) {
                tm = tm.createForSubscriptionId(defaultSmsSub);
                String line1Number = tm.getLine1Number();
                if (!TextUtils.isEmpty(line1Number)) {
                    return line1Number;
                }
            }
        } else {
            Toast.makeText(this, "Couldn't resolve line 1 due to permissions error.",
                    Toast.LENGTH_LONG).show();
        }
        return result;
    }
}
