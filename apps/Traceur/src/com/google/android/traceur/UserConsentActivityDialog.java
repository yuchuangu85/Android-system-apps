/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.traceur;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.widget.CheckBox;

import java.io.File;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * Dialog that warns about contents of a trace.
 * Adapted from fw/base/packages/Shell's BugreportWarningActivity.
 */
public class UserConsentActivityDialog extends AlertActivity
        implements DialogInterface.OnClickListener {

    private static final String PREF_KEY_SHOW_DIALOG = "show-dialog";
    private static final int PREF_STATE_SHOW = 0;
    private static final int PREF_STATE_HIDE = 1;

    private Intent mNextIntent;
    private CheckBox mDontShowAgain;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mNextIntent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT);

        // If the user has previously indicated to never show this dialog again,
        // go ahead and start the target intent and finish this activity.
        if (getShowDialogState(this) == PREF_STATE_HIDE) {
            startActivity(mNextIntent);
            finish();
        }

        final AlertController.AlertParams params = mAlertParams;
        params.mView = LayoutInflater.from(this).inflate(
            R.layout.consent_dialog_checkbox, null);
        params.mTitle = getString(R.string.share_trace);
        params.mMessage = getString(R.string.system_trace_sensitive_data);
        params.mPositiveButtonText = getString(R.string.share);
        params.mNegativeButtonText = getString(android.R.string.cancel);
        params.mPositiveButtonListener = this;
        params.mNegativeButtonListener = this;

        mDontShowAgain = (CheckBox) params.mView.findViewById(android.R.id.checkbox);

        setupAlert();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == AlertDialog.BUTTON_POSITIVE) {
            if (mDontShowAgain.isChecked()) {
                setShowDialogState(this, PREF_STATE_HIDE);
            }
            startActivity(mNextIntent);
        }

        finish();
    }

    private int getShowDialogState(Context context) {
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(PREF_KEY_SHOW_DIALOG, PREF_STATE_SHOW);
    }

    private void setShowDialogState(Context context, int value) {
        final SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putInt(PREF_KEY_SHOW_DIALOG, value).apply();
    }
}
