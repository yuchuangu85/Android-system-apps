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

import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.text.TextUtils;

import androidx.core.app.JobIntentService;

import com.android.car.dialer.Constants;
import com.android.car.dialer.telecom.UiCallManager;
import com.android.car.telephony.common.TelecomUtils;

import java.util.List;

/**
 * A {@link JobIntentService} that is used to handle actions from notifications to:
 * <ul><li>answer or inject an incoming call.
 * <li>call back or message to a missed call.
 */
public class NotificationService extends JobIntentService {
    static final String ACTION_ANSWER_CALL = "CD.ACTION_ANSWER_CALL";
    static final String ACTION_DECLINE_CALL = "CD.ACTION_DECLINE_CALL";
    static final String ACTION_CALL_BACK_MISSED = "CD.ACTION_CALL_BACK_MISSED";
    static final String ACTION_MESSAGE_MISSED = "CD.ACTION_MESSAGE_MISSED";
    static final String ACTION_READ_ALL_MISSED = "CD.ACTION_READ_ALL_MISSED";
    static final String EXTRA_CALL_ID = "CD.EXTRA_CALL_ID";

    /** Create an intent to handle reading all missed call action and schedule for executing. */
    public static void readAllMissedCall(Context context) {
        Intent readAllMissedCallIntent = new Intent(context, NotificationReceiver.class);
        readAllMissedCallIntent.setAction(ACTION_READ_ALL_MISSED);
        enqueueWork(context, readAllMissedCallIntent);
    }

    /** Enqueue the intent. */
    static void enqueueWork(Context context, Intent intent) {
        enqueueWork(
                context, NotificationService.class, Constants.JobIds.NOTIFICATION_SERVICE, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        String action = intent.getAction();
        String callId = intent.getStringExtra(EXTRA_CALL_ID);
        switch (action) {
            case ACTION_ANSWER_CALL:
                answerCall(callId);
                break;
            case ACTION_DECLINE_CALL:
                declineCall(callId);
                break;
            case ACTION_CALL_BACK_MISSED:
                UiCallManager.get().placeCall(callId);
                TelecomUtils.markCallLogAsRead(getApplicationContext(), callId);
                break;
            case ACTION_MESSAGE_MISSED:
                // TODO: call assistant to send message
                TelecomUtils.markCallLogAsRead(getApplicationContext(), callId);
                break;
            case ACTION_READ_ALL_MISSED:
                TelecomUtils.markCallLogAsRead(getApplicationContext(), callId);
                break;
            default:
                break;
        }
    }

    private void answerCall(String callId) {
        List<Call> callList = UiCallManager.get().getCallList();
        for (Call call : callList) {
            if (call.getDetails() != null
                    && TextUtils.equals(call.getDetails().getTelecomCallId(), callId)) {
                call.answer(/* videoState= */0);
                return;
            }
        }
    }

    private void declineCall(String callId) {
        List<Call> callList = UiCallManager.get().getCallList();
        for (Call call : callList) {
            if (call.getDetails() != null
                    && TextUtils.equals(call.getDetails().getTelecomCallId(), callId)) {
                call.reject(false, /* textMessage= */"");
                return;
            }
        }
    }
}
