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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.Log;

/**
 * Dialog activity used when there is an ongoing call redirected by the call redirection service.
 * The dialog prompts the user to inform the redirected outgoing call is canceled due to timeout.
 */
public class CallRedirectionTimeoutDialogActivity extends Activity {
    public static final String EXTRA_REDIRECTION_APP_NAME =
            "android.telecom.extra.REDIRECTION_APP_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(this, "CallRedirectionTimeoutDialogActivity onCreate.");
        final CharSequence redirectionAppName = getIntent().getStringExtra(
                EXTRA_REDIRECTION_APP_NAME);
        showDialog(redirectionAppName);
    }

    private void showDialog(final CharSequence redirectionAppName) {
        Log.i(this, "showDialog: timeout redirection with %s", redirectionAppName);
        CharSequence message = getString(
                R.string.alert_redirect_outgoing_call_timeout, redirectionAppName);
        final AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        finish();
                    }
                })
                .create();

        errorDialog.show();
    }
}
