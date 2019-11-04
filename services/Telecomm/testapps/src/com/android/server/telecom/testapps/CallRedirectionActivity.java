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

package com.android.server.telecom.testapps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.telecom.Log;

public class CallRedirectionActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(this, "onCreate: CallRedirectionActivity");

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Test Call Redirection")
                .setMessage("Decision for call redirection?")
                .setNegativeButton("Timeout", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // No action is needed for timeout
                        finish();
                    }
                })
                .setPositiveButton("Redirect and confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (TestCallRedirectionService.getInstance() != null) {
                            TestCallRedirectionService.getInstance().tryRedirectCallAndAskToConfirm();
                        }
                        finish();
                    }
                }).create();
        alertDialog.show();
    }
}