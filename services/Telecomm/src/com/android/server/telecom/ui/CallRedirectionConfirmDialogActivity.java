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
 * limitations under the License
 */

package com.android.server.telecom.ui;

import com.android.server.telecom.R;
import com.android.server.telecom.TelecomBroadcastIntentProcessor;
import com.android.server.telecom.components.TelecomBroadcastReceiver;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.telecom.Log;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.Button;

/**
 * Dialog activity used when there is an ongoing call redirected by the call redirection service.
 * The dialog prompts the user to see if they want to place the redirected outgoing call.
 */
public class CallRedirectionConfirmDialogActivity extends Activity {
    public static final String EXTRA_REDIRECTION_OUTGOING_CALL_ID =
            "android.telecom.extra.REDIRECTION_OUTGOING_CALL_ID";
    public static final String EXTRA_REDIRECTION_APP_NAME =
            "android.telecom.extra.REDIRECTION_APP_NAME";

    private String mCallId;
    private AlertDialog mConfirmDialog;
    /**
     * Tracks whether the activity has stopped due to a loss of focus (e.g. use hitting the home
     * button) or whether its going to stop because a button in the dialog was pressed.
     */
    private boolean mHasLostFocus = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(this, "CallRedirectionConfirmDialogActivity onCreate.");
        final CharSequence redirectionAppName = getIntent().getStringExtra(
                EXTRA_REDIRECTION_APP_NAME);
        mCallId = getIntent().getStringExtra(EXTRA_REDIRECTION_OUTGOING_CALL_ID);
        showDialog(redirectionAppName);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mHasLostFocus) {
            Log.i(this, "onStop: dialog lost focus; canceling redirection for call %s", mCallId);
            mConfirmDialog.dismiss();
            cancelRedirection();
        }
    }

    private void showDialog(final CharSequence redirectionAppName) {
        Log.i(this, "showDialog: confirming redirection with %s", redirectionAppName);

        mConfirmDialog = new AlertDialog.Builder(this).create();
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View dialogView = layoutInflater.inflate(R.layout.call_redirection_confirm_dialog, null);

        Button buttonFirstLine = (Button) dialogView.findViewById(R.id.buttonFirstLine);
        buttonFirstLine.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent proceedWithoutRedirectedCall = new Intent(
                        TelecomBroadcastIntentProcessor.ACTION_PLACE_UNREDIRECTED_CALL,
                        null, CallRedirectionConfirmDialogActivity.this,
                        TelecomBroadcastReceiver.class);
                proceedWithoutRedirectedCall.putExtra(EXTRA_REDIRECTION_OUTGOING_CALL_ID, mCallId);
                sendBroadcast(proceedWithoutRedirectedCall);
                mConfirmDialog.dismiss();
                mHasLostFocus = false;
                finish();
            }
        });

        Button buttonSecondLine = (Button) dialogView.findViewById(R.id.buttonSecondLine);
        buttonSecondLine.setText(getString(R.string.alert_place_outgoing_call_with_redirection,
                redirectionAppName));
        buttonSecondLine.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent proceedWithRedirectedCall = new Intent(
                        TelecomBroadcastIntentProcessor
                                .ACTION_PLACE_REDIRECTED_CALL, null,
                        CallRedirectionConfirmDialogActivity.this,
                        TelecomBroadcastReceiver.class);
                proceedWithRedirectedCall.putExtra(EXTRA_REDIRECTION_OUTGOING_CALL_ID, mCallId);
                sendBroadcast(proceedWithRedirectedCall);
                mConfirmDialog.dismiss();
                mHasLostFocus = false;
                finish();
            }
        });

        Button buttonThirdLine = (Button) dialogView.findViewById(R.id.buttonThirdLine);
        buttonThirdLine.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent cancelRedirectedCall = new Intent(
                        TelecomBroadcastIntentProcessor.ACTION_CANCEL_REDIRECTED_CALL,
                        null, CallRedirectionConfirmDialogActivity.this,
                        TelecomBroadcastReceiver.class);
                cancelRedirectedCall.putExtra(EXTRA_REDIRECTION_OUTGOING_CALL_ID, mCallId);
                sendBroadcast(cancelRedirectedCall);
                mConfirmDialog.dismiss();
                mHasLostFocus = false;
                finish();
            }
        });

        mConfirmDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                cancelRedirection();
                dialog.dismiss();
                mHasLostFocus = false;
                finish();
            }
        });

        mConfirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        mConfirmDialog.setCancelable(false);
        mConfirmDialog.setCanceledOnTouchOutside(false);
        mConfirmDialog.setView(dialogView);

        mConfirmDialog.show();
    }

    /**
     * Signals to Telecom that redirection of the call is to be cancelled.
     */
    private void cancelRedirection() {
        Intent cancelRedirectedCall = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_CANCEL_REDIRECTED_CALL,
                null, CallRedirectionConfirmDialogActivity.this,
                TelecomBroadcastReceiver.class);
        cancelRedirectedCall.putExtra(EXTRA_REDIRECTION_OUTGOING_CALL_ID, mCallId);
        sendBroadcast(cancelRedirectedCall);
    }
}
