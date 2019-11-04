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

package com.android.phone.settings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.IIntegerConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * Trampolines a request to Settings to get the SMS subscription associated with an SmsManager
 * operation.
 *
 * Since a Service can not start an Activity with
 * {@link Activity#startActivityForResult(Intent, int)} and get a response (only Activities can
 * handle the results), we have to "Trampoline" this operation by creating an empty Activity whose
 * only job is to call startActivityForResult with the correct Intent and handle the result.
 */
// TODO: SmsManager should be constructed with an activity context so it can start as part of its
// task and fall back to PickSmsSubscriptionActivity being called in PhoneInterfaceManager if not
// called from an activity context.
public class PickSmsSubscriptionActivity extends Activity {

    private static final String LOG_TAG = "PickSmsSubActivity";

    // Defined in Settings SimDialogActivity
    private static final String RESULT_SUB_ID = "result_sub_id";
    public static final String DIALOG_TYPE_KEY = "dialog_type";
    public static final int SMS_PICK_FOR_MESSAGE = 4;

    private static final ComponentName SETTINGS_SUB_PICK_ACTIVITY = new ComponentName(
            "com.android.settings", "com.android.settings.sim.SimDialogActivity");

    private static final List<IIntegerConsumer> sSmsPickPendingList = new ArrayList<>();

    private static final int REQUEST_GET_SMS_SUB_ID = 1;

    /**
     * Adds a consumer to the list of pending results that will be accepted once the activity
     * completes.
     */
    public static void addPendingResult(IIntegerConsumer consumer) {
        synchronized (sSmsPickPendingList) {
            sSmsPickPendingList.add(consumer);
        }
        Log.i(LOG_TAG, "queue pending result, token: " + consumer);
    }

    private static void sendResultAndClear(int resultId) {
        // If the calling process died, just ignore callback.
        synchronized (sSmsPickPendingList) {
            for (IIntegerConsumer c : sSmsPickPendingList) {
                try {
                    c.accept(resultId);
                    Log.i(LOG_TAG, "Result received, token: " + c + ", result: " + resultId);
                } catch (RemoteException e) {
                    // The calling process died, skip this one.
                }
            }
            sSmsPickPendingList.clear();
        }
    }

    // Keep track if this activity has been stopped (i.e. user navigated away, power screen off,...)
    // if so, treat it as the user navigating away and end the task if it is restarted without an
    // onCreate/onNewIntent.
    private boolean mPreviouslyStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreviouslyStopped = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        mPreviouslyStopped = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This is cause a little jank with the recents display, but there is no other way to handle
        // the case where activity has stopped and we want to dismiss the dialog. We use the
        // tag "excludeFromRecents", but in the cases where it is still shown, kill it in onResume.
        if (mPreviouslyStopped) {
            finishAndRemoveTask();
        } else {
            launchSmsPicker(new Intent(getIntent()));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // User navigated away from dialog, send invalid sub id result.
        mPreviouslyStopped = true;
        sendResultAndClear(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        // triggers cancelled result for onActivityResult
        finishActivity(REQUEST_GET_SMS_SUB_ID);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GET_SMS_SUB_ID) {
            int result = data == null ? SubscriptionManager.INVALID_SUBSCRIPTION_ID :
                    data.getIntExtra(RESULT_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            if (resultCode == Activity.RESULT_OK) {
                sendResultAndClear(result);
            } else {
                sendResultAndClear(SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            }
        }
        // This will be handled in onResume - we do not want to call this all the time here because
        // we need to be able to restart if stopped and a new intent comes in via onNewIntent.
        if (!mPreviouslyStopped) {
            finishAndRemoveTask();
        }
    }

    private void launchSmsPicker(Intent trampolineIntent) {
        trampolineIntent.setComponent(SETTINGS_SUB_PICK_ACTIVITY);
        // Remove this flag if it exists, we want the settings activity to be part of this task.
        trampolineIntent.removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(trampolineIntent, REQUEST_GET_SMS_SUB_ID);
    }
}
